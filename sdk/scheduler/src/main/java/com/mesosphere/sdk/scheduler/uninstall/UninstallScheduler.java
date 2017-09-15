package com.mesosphere.sdk.scheduler.uninstall;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.PlansResource;
import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.*;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import org.apache.mesos.Protos;
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
    private final SchedulerApiServer schedulerApiServer;

    // Initialized when registration completes (and when we have the SchedulerDriver):
    private OfferAccepter offerAccepter;

    /**
     * Creates a new UninstallScheduler based on the provided API port and initialization timeout,
     * and a {@link StateStore}. The UninstallScheduler builds an uninstall {@link Plan} with two {@link Phase}s:
     * a resource phase where all reserved resources get released back to Mesos, and a deregister phase where
     * the framework deregisters itself and cleans up its state in Zookeeper.
     */
    public UninstallScheduler(
            String serviceName,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerFlags schedulerFlags,
            Optional<SecretsClient> secretsClient) {
        super(stateStore, configStore);
        this.uninstallPlanBuilder =
                new UninstallPlanBuilder(serviceName, stateStore, configStore, schedulerFlags, secretsClient);
        this.uninstallPlanManager = new DefaultPlanManager(uninstallPlanBuilder.getPlan());
        LOGGER.info("Initializing plans resource...");
        this.schedulerApiServer = new SchedulerApiServer(
                schedulerFlags.getApiServerPort(),
                Collections.singletonList(new PlansResource(Collections.singletonList(uninstallPlanManager))),
                schedulerFlags.getApiServerInitTimeout());
        new Thread(schedulerApiServer).start();
    }

    public UninstallScheduler(
            String serviceName,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerFlags schedulerFlags) {
        this(serviceName, stateStore, configStore, schedulerFlags, Optional.empty());
    }

    @Override
    protected void initialize(SchedulerDriver driver) throws InterruptedException {
        LOGGER.info("Initializing...");
        // NOTE: We wait until this point to perform any work using configStore/stateStore.
        // We specifically avoid writing any data to ZK before registered() has been called.
        initializeGlobals(driver);
        LOGGER.info("Proceeding with uninstall plan...");
        uninstallPlanManager.getPlan().proceed();
        LOGGER.info("Done initializing.");
    }

    private void initializeGlobals(SchedulerDriver driver) {
        LOGGER.info("Initializing globals...");
        // Now that our SchedulerDriver has been passed in by Mesos, we can give it to the DeregisterStep in the Plan.
        uninstallPlanBuilder.registered(driver);
        offerAccepter = new OfferAccepter(Collections.singletonList(
                new UninstallRecorder(stateStore, uninstallPlanBuilder.getResourceSteps())));
    }

    public boolean apiServerReady() {
        return schedulerApiServer.ready();
    }

    @Override
    protected void executePlans(List<Protos.Offer> offers) {
        List<Protos.Offer> localOffers = new ArrayList<>(offers);
        // Get candidate steps to be scheduled
        Collection<? extends Step> candidateSteps = uninstallPlanManager.getCandidates(Collections.emptyList());
        if (!candidateSteps.isEmpty()) {
            LOGGER.info("Attempting to process these candidates from uninstall plan: {}",
                    candidateSteps.stream().map(Element::getName).collect(Collectors.toList()));
            candidateSteps.forEach(Step::start);
        }

        // Destroy/Unreserve any reserved resource or volume that is offered
        final List<Protos.OfferID> offersWithReservedResources = new ArrayList<>();

        offersWithReservedResources.addAll(
                new ResourceCleanerScheduler(new UninstallResourceCleaner(), offerAccepter)
                        .resourceOffers(driver, localOffers));

        // Decline remaining offers.
        List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(localOffers, offersWithReservedResources);
        OfferUtils.declineOffers(driver, unusedOffers);
    }

    @Override
    protected Collection<PlanManager> getPlanManagers() {
        return Arrays.asList(uninstallPlanManager);
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        LOGGER.info("Received status update for taskId={} state={} message={} protobuf={}",
                status.getTaskId().getValue(),
                status.getState().toString(),
                status.getMessage(),
                TextFormat.shortDebugString(status));

        eventBus.post(status);

        try {
            stateStore.storeStatus(StateStoreUtils.getTaskName(stateStore, status), status);
            reconciler.update(status);
        } catch (Exception e) {
            LOGGER.warn(String.format("Failed to handle TaskStatus received from Mesos. "
                    + "This may be expected if Mesos sent stale status information: %s",
                    TextFormat.shortDebugString(status)), e);
        }
    }

    @VisibleForTesting
    Plan getPlan() {
        return uninstallPlanManager.getPlan();
    }
}
