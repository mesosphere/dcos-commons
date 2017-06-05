package com.mesosphere.sdk.scheduler.uninstall;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.api.PlansResource;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.DefaultTaskKiller;
import com.mesosphere.sdk.scheduler.SchedulerApiServer;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.DefaultTaskFailureListener;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.TOMBSTONE_MARKER;

/**
 * This scheduler uninstalls the framework and releases all of its resources.
 */
public class UninstallScheduler extends AbstractScheduler {

    private static final String RESOURCE_PHASE = "resource-phase";
    private static final String DEREGISTER_PHASE = "deregister-phase";
    private static final Logger LOGGER = LoggerFactory.getLogger(UninstallScheduler.class);
    protected final int port;
    private final Plan uninstallPlan;
    private final ConfigStore<ServiceSpec> configStore;
    PlanManager uninstallPlanManager;
    private TaskKiller taskKiller;
    private OfferAccepter offerAccepter;
    private SchedulerApiServer schedulerApiServer;

    /**
     * Creates a new UninstallScheduler based on the provided API port and initialization timeout,
     * and a {@link StateStore}. The UninstallScheduler builds an uninstall {@link Plan} with two {@link Phase}s:
     * a resource phase where all reserved resources get released back to Mesos, and a deregister phase where
     * the framework deregisters itself and cleans up its state in Zookeeper.
     */
    public UninstallScheduler(
            int port,
            Duration apiServerInitTimeout,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore) {
        super(stateStore);
        this.port = port;
        this.configStore = configStore;
        this.uninstallPlan = getPlan();
        this.uninstallPlanManager = new DefaultPlanManager(uninstallPlan);
        LOGGER.info("Initializing plans resource...");
        PlansResource plansResource = new PlansResource(Collections.singletonList(uninstallPlanManager));
        Collection<Object> apiResources = Collections.singletonList(plansResource);
        schedulerApiServer = new SchedulerApiServer(port, apiResources, apiServerInitTimeout);
        new Thread(schedulerApiServer).start();
    }

    private Plan getPlan() {
        // If there is no framework ID, wipe ZK and return a COMPLETE plan
        if (!stateStore.fetchFrameworkId().isPresent()) {
            LOGGER.info("There is no framework ID so clear service in StateStore and return a COMPLETE plan");
            stateStore.clearAllData();
            return new DefaultPlan(Constants.DEPLOY_PLAN_NAME, Collections.emptyList());
        }

        // create one UninstallStep per unique Resource, including Executor resources
        // We filter to unique Resource Id's, because Executor level resources are tracked
        // on multiple Tasks.
        List<Protos.Resource> allResources = ResourceCollectionUtils.getAllResources(stateStore.fetchTasks());
        List<Step> taskSteps = ResourceCollectionUtils.getResourceIds(allResources).stream()
                .map(resourceId -> new UninstallStep(resourceId, resourceId.startsWith(TOMBSTONE_MARKER) ?
                        Status.COMPLETE : Status.PENDING))
                .collect(Collectors.toList());
        Phase resourcePhase = new DefaultPhase(RESOURCE_PHASE, taskSteps, new ParallelStrategy<>(),
                Collections.emptyList());

        // We don't have access to the SchedulerDriver yet, so that gets set later
        Step deregisterStep = new DeregisterStep(Status.PENDING, stateStore);
        List<Step> deregisterSteps = Collections.singletonList(deregisterStep);
        Phase deregisterPhase = new DefaultPhase(DEREGISTER_PHASE, deregisterSteps, new SerialStrategy<>(),
                Collections.emptyList());

        List<Phase> phases = Arrays.asList(resourcePhase, deregisterPhase);
        return new DefaultPlan(Constants.DEPLOY_PLAN_NAME, phases);
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
        taskKiller = new DefaultTaskKiller(new DefaultTaskFailureListener(stateStore, configStore), driver);
        Phase resourcePhase = uninstallPlan.getChildren().get(0);
        UninstallRecorder uninstallRecorder = new UninstallRecorder(stateStore, resourcePhase);
        offerAccepter = new OfferAccepter(Collections.singletonList(uninstallRecorder));
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offersToProcess) {
        List<Protos.Offer> offers = new ArrayList<>(offersToProcess);
        if (!apiServerReady()) {
            LOGGER.info("Declining all offers. Waiting for API Server to start ...");
            OfferUtils.declineOffers(driver, offersToProcess);
            return;
        }

        LOGGER.info("Received {} {}:", offers.size(), offers.size() == 1 ? "offer" : "offers");
        for (int i = 0; i < offers.size(); ++i) {
            LOGGER.info("  {}: {}", i + 1, TextFormat.shortDebugString(offers.get(i)));
        }

        reconciler.reconcile(driver);
        if (!reconciler.isReconciled()) {
            LOGGER.info("Reconciliation is still in progress, declining all offers.");
            OfferUtils.declineOffers(driver, offers);
            return;
        }

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
                        .resourceOffers(driver, offers));

        List<Protos.Offer> unusedOffers = OfferUtils.filterOutAcceptedOffers(offers, offersWithReservedResources);

        // Decline remaining offers.
        OfferUtils.declineOffers(driver, unusedOffers);

    }

    public boolean apiServerReady() {
        return schedulerApiServer.ready();
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        LOGGER.info("Received status update for taskId={} state={} message='{}'",
                status.getTaskId().getValue(),
                status.getState().toString(),
                status.getMessage());

        try {
            stateStore.storeStatus(status);
            reconciler.update(status);
        } catch (Exception e) {
            LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                    + "This may be expected if Mesos sent stale status information: " + status, e);
        }
    }

    @Override
    protected void postRegister() {
        super.postRegister();
        // Now that our SchedulerDriver has been passed in by Mesos, we can give it to the DeregisterStep.
        // It's the second Step of the second Phase of the Plan.
        DeregisterStep deregisterStep = (DeregisterStep) uninstallPlan.getChildren().get(1).getChildren().get(0);
        deregisterStep.setSchedulerDriver(driver);

        Collection<String> taskNames = stateStore.fetchTaskNames();
        LOGGER.info("Found {} tasks to restart and clear: {}", taskNames.size(), taskNames);
        for (String taskName : taskNames) {
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
            taskInfoOptional.ifPresent(taskInfo -> taskKiller.killTask(taskInfo.getTaskId(), false));
        }
    }
}
