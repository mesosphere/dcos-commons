package com.mesosphere.sdk.scheduler.uninstall;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.HealthResource;
import com.mesosphere.sdk.api.PlansResource;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This scheduler uninstalls the framework and releases all of its resources.
 */
public class UninstallScheduler extends AbstractScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UninstallScheduler.class);

    private final UninstallPlanBuilder uninstallPlanBuilder;
    private final PlanManager uninstallPlanManager;
    private final Collection<Object> resources;

    private OfferAccepter offerAccepter;

    /**
     * Creates a new {@link UninstallScheduler} based on the provided API port and initialization timeout, and a
     * {@link StateStore}. The {@link UninstallScheduler} builds an uninstall {@link Plan} which will clean up the
     * service's reservations, TLS artifacts, zookeeper data, and any other artifacts from running the service.
     */
    public UninstallScheduler(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerConfig schedulerConfig) {
        this(serviceSpec, stateStore, configStore, schedulerConfig, Optional.empty());
    }

    protected UninstallScheduler(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerConfig schedulerConfig,
            Optional<SecretsClient> customSecretsClientForTests) {
        super(stateStore, configStore, schedulerConfig);
        uninstallPlanBuilder = new UninstallPlanBuilder(
                serviceSpec, stateStore, configStore, schedulerConfig, customSecretsClientForTests);
        uninstallPlanManager = DefaultPlanManager.createProceeding(uninstallPlanBuilder.getPlan());
        resources = Arrays.<Object>asList(
                new PlansResource().setPlanManagers(Collections.singletonList(uninstallPlanManager)),
                new HealthResource().setHealthyPlanManagers(Collections.singletonList(uninstallPlanManager)));
    }

    @Override
    public Optional<Scheduler> getMesosScheduler() {
        if (allButStateStoreUninstalled(stateStore, schedulerConfig)) {
            LOGGER.info("Not registering framework because there are no resources left to unreserve.");
            return Optional.empty();
        }

        return super.getMesosScheduler();
    }

    @Override
    public Collection<Object> getResources() {
        return resources;
    }

    @Override
    protected PlanCoordinator initialize(SchedulerDriver driver) throws InterruptedException {
        LOGGER.info("Initializing...");

        // NOTE: We wait until this point to perform any work using configStore/stateStore.
        // We specifically avoid writing any data to ZK before registered() has been called.

        // Now that our SchedulerDriver has been passed in by Mesos, we can give it to the DeregisterStep in the Plan.
        uninstallPlanBuilder.registered(driver);
        offerAccepter = new OfferAccepter(Collections.singletonList(
                new UninstallRecorder(stateStore, uninstallPlanBuilder.getResourceSteps())));

        LOGGER.info("Done initializing.");

        // Return a stub coordinator which only does work against the sole plan manager.
        return new PlanCoordinator() {
            @Override
            public List<Step> getCandidates() {
                return new ArrayList<>(uninstallPlanManager.getCandidates(Collections.emptyList()));
            }

            @Override
            public Collection<PlanManager> getPlanManagers() {
                return Collections.singletonList(uninstallPlanManager);
            }
        };
    }

    @Override
    protected void processOffers(SchedulerDriver driver, List<Protos.Offer> offers, Collection<Step> steps) {
        List<Protos.Offer> localOffers = new ArrayList<>(offers);
        // Get candidate steps to be scheduled
        if (!steps.isEmpty()) {
            LOGGER.info("Attempting to process {} candidates from uninstall plan: {}",
                    steps.size(), steps.stream().map(Element::getName).collect(Collectors.toList()));
            steps.forEach(Step::start);
        }

        // Destroy/Unreserve any reserved resource or volume that is offered
        final List<Protos.OfferID> offersWithReservedResources = new ArrayList<>();

        ResourceCleanerScheduler rcs = new ResourceCleanerScheduler(new UninstallResourceCleaner(), offerAccepter);

        offersWithReservedResources.addAll(rcs.resourceOffers(driver, localOffers));

        // Decline remaining offers.
        List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(localOffers, offersWithReservedResources);
        if (unusedOffers.isEmpty()) {
            LOGGER.info("No offers to be declined.");
        } else {
            LOGGER.info("Declining {} unused offers", unusedOffers.size());
            OfferUtils.declineLong(driver, unusedOffers);
        }
    }

    @Override
    protected void processStatusUpdate(Protos.TaskStatus status) {
        stateStore.storeStatus(StateStoreUtils.getTaskName(stateStore, status), status);
    }

    private static boolean allButStateStoreUninstalled(StateStore stateStore, SchedulerConfig schedulerConfig) {
        // Because we cannot delete the root ZK node (ACLs on the master, see StateStore.clearAllData() for more
        // details) we have to clear everything under it. This results in a race condition, where DefaultService can
        // have register() called after the StateStore already has the uninstall bit wiped.
        //
        // As can be seen in DefaultService.initService(), DefaultService.register() will only be called in uninstall
        // mode if schedulerConfig.isUninstallEnabled() == true. Therefore we can use it as an OR along with
        // StateStoreUtils.isUninstalling().

        // resources are destroyed and unreserved, framework ID is gone, but tasks still need to be cleared
        return !stateStore.fetchFrameworkId().isPresent() &&
                ResourceUtils.getResourceIds(
                        ResourceUtils.getAllResources(stateStore.fetchTasks())).stream()
                        .allMatch(resourceId -> resourceId.startsWith(Constants.TOMBSTONE_MARKER));
    }

    @VisibleForTesting
    Plan getPlan() {
        return uninstallPlanManager.getPlan();
    }
}
