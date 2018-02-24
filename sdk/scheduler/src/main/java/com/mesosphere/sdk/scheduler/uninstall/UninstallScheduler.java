package com.mesosphere.sdk.scheduler.uninstall;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.http.HealthResource;
import com.mesosphere.sdk.http.DeprecatedPlanResource;
import com.mesosphere.sdk.http.PlansResource;
import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.ServiceScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This scheduler uninstalls a service and releases all of its resources.
 */
public class UninstallScheduler extends ServiceScheduler {
    /**
     * Empty complete deploy plan to be used if the scheduler was launched in a finished state.
     */
    @VisibleForTesting
    static final Plan EMPTY_DEPLOY_PLAN = new DefaultPlan(Constants.DEPLOY_PLAN_NAME, Collections.emptyList());

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Optional<SecretsClient> secretsClient;

    private PlanManager uninstallPlanManager;
    private Collection<Object> defaultResources = Collections.emptyList();
    private OfferAccepter offerAccepter;

    /**
     * Creates a new {@link UninstallScheduler} based on the provided API port and initialization timeout, and a
     * {@link StateStore}. The {@link UninstallScheduler} builds an uninstall {@link Plan} which will clean up
     * the service's reservations, TLS artifacts, zookeeper data, and any other artifacts from running the service.
     */
    public UninstallScheduler(
            ServiceSpec serviceSpec,
            FrameworkStore frameworkStore,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerConfig schedulerConfig,
            Optional<PlanCustomizer> planCustomizer) {
        this(serviceSpec, frameworkStore, stateStore, configStore, schedulerConfig, planCustomizer, Optional.empty());
    }

    protected UninstallScheduler(
            ServiceSpec serviceSpec,
            FrameworkStore frameworkStore,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerConfig schedulerConfig,
            Optional<PlanCustomizer> planCustomizer,
            Optional<SecretsClient> customSecretsClientForTests) {
        super(frameworkStore, stateStore, schedulerConfig, planCustomizer);
        this.secretsClient = customSecretsClientForTests;

        final Plan deployPlan;
        if (allButStateStoreUninstalled(frameworkStore, stateStore, schedulerConfig)) {
            /**
             * If the state store is empty this scheduler has been deregistered. Therefore it should report itself
             * healthy and provide an empty COMPLETE deploy plan so it may complete its uninstall.
             */
            deployPlan = EMPTY_DEPLOY_PLAN;
        } else {
            deployPlan = new UninstallPlanBuilder(
                    serviceSpec,
                    frameworkStore,
                    stateStore,
                    configStore,
                    schedulerConfig,
                    secretsClient)
                    .build();
        }

        this.uninstallPlanManager = DefaultPlanManager.createProceeding(deployPlan);
        PlansResource plansResource = new PlansResource()
                .setPlanManagers(Collections.singletonList(uninstallPlanManager));
        this.defaultResources = Arrays.asList(
                plansResource,
                new DeprecatedPlanResource(plansResource),
                new HealthResource().setHealthyPlanManagers(Collections.singletonList(uninstallPlanManager)));

        List<ResourceCleanupStep> resourceCleanupSteps = deployPlan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step instanceof ResourceCleanupStep)
                .map(step -> (ResourceCleanupStep) step)
                .collect(Collectors.toList());
        this.offerAccepter = new OfferAccepter(Collections.singletonList(
                new UninstallRecorder(stateStore, resourceCleanupSteps)));

        try {
            logger.info("Uninstall plan set to: {}", SerializationUtils.toJsonString(PlanInfo.forPlan(deployPlan)));
        } catch (IOException e) {
            logger.error("Failed to deserialize uninstall plan.");
        }
    }

    /**
     * Returns whether the process should register with Mesos.
     *
     * This handles the case where there's nothing left to do with Mesos -- the framework has already unregistered.
     */
    public boolean shouldRegisterFramework() {
        return !allButStateStoreUninstalled(frameworkStore, stateStore, schedulerConfig);
    }

    @Override
    public void setResourceServer(ResourceServer resourceServer) {
        resourceServer.addResources("v1", defaultResources);
    }

    @Override
    protected PlanCoordinator getPlanCoordinator() {
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
    protected void registeredWithMesos() {
        logger.info("Uninstall scheduler registered with Mesos.");
    }

    @Override
    protected List<Protos.Offer> processOffers(List<Protos.Offer> offers, Collection<Step> steps) {
        List<Protos.Offer> localOffers = new ArrayList<>(offers);
        // Get candidate steps to be scheduled
        if (!steps.isEmpty()) {
            logger.info("Attempting to process {} candidates from uninstall plan: {}",
                    steps.size(), steps.stream().map(Element::getName).collect(Collectors.toList()));
            steps.forEach(Step::start);
        }

        // Destroy/Unreserve any reserved resource or volume that is offered
        final List<Protos.OfferID> offersWithReservedResources = new ArrayList<>();

        ResourceCleanerScheduler rcs = new ResourceCleanerScheduler(new UninstallResourceCleaner(), offerAccepter);

        offersWithReservedResources.addAll(rcs.resourceOffers(localOffers));

        // Return remaining offers.
        return OfferUtils.filterOutAcceptedOffers(localOffers, offersWithReservedResources);
    }

    @Override
    protected void processStatusUpdate(Protos.TaskStatus status) throws Exception {
        stateStore.storeStatus(StateStoreUtils.getTaskName(stateStore, status), status);
    }

    private static boolean allButStateStoreUninstalled(
            FrameworkStore frameworkStore, StateStore stateStore, SchedulerConfig schedulerConfig) {
        // Because we cannot delete the root ZK node (ACLs on the master, see StateStore.clearAllData() for more
        // details) we have to clear everything under it. This results in a race condition, where DefaultService can
        // have register() called after the StateStore already has the uninstall bit wiped.
        //
        // As can be seen in DefaultService.initService(), DefaultService.register() will only be called in uninstall
        // mode if schedulerConfig.isUninstallEnabled() == true. Therefore we can use it as an OR along with
        // StateStoreUtils.isUninstalling().

        // resources are destroyed and unreserved, framework ID is gone, but tasks still need to be cleared
        return !frameworkStore.fetchFrameworkId().isPresent() &&
                ResourceUtils.getResourceIds(
                        ResourceUtils.getAllResources(stateStore.fetchTasks())).stream()
                        .allMatch(resourceId -> resourceId.startsWith(Constants.TOMBSTONE_MARKER));
    }
}
