package org.apache.mesos.scheduler;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.offer.*;
import org.apache.mesos.reconciliation.DefaultReconciler;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.api.PlanResource;
import org.apache.mesos.scheduler.plan.api.PlansResource;
import org.apache.mesos.scheduler.recovery.*;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.constrain.TimedLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.TimedFailureMonitor;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.PersistentOperationRecorder;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.api.StateResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * This scheduler when provided with a ServiceSpecification will deploy the service and recover from encountered faults
 * when possible.  Changes to the ServiceSpecification will result in rolling configuration updates, or the creation of
 * new Tasks where applicable.
 */
public class DefaultScheduler implements Scheduler {
    private static final Integer DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC = 10 * 60;
    private static final Integer PERMANENT_FAILURE_DELAY_SEC = 20 * 60;
    private static final Integer AWAIT_TERMINATION_TIMEOUT_MS = 10000;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ServiceSpecification serviceSpecification;
    private final String zkConnectionString;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final BlockingQueue<Collection<Object>> resourcesQueue;

    private Reconciler reconciler;
    private StateStore stateStore;
    private TaskFailureListener taskFailureListener;
    private TaskKiller taskKiller;
    private OfferAccepter offerAccepter;
    private Plan deployPlan;
    private PlanManager deployPlanManager;
    private PlanScheduler planScheduler;
    private PlanManager recoveryPlanManager;
    private PlanCoordinator planCoordinator;
    private Collection<Object> resources;

    public DefaultScheduler(ServiceSpecification serviceSpecification) {
        this(serviceSpecification, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    public DefaultScheduler(ServiceSpecification serviceSpecification, String zkConnectionString) {
        this.serviceSpecification = serviceSpecification;
        this.zkConnectionString = zkConnectionString;
        this.resourcesQueue = new ArrayBlockingQueue<>(1);
    }

    public Collection<Object> getResources() throws InterruptedException {
        if (resources == null) {
            resources = resourcesQueue.take();
        }

        return resources;
    }

    void awaitTermination() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(AWAIT_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    Plan getPlan() {
        return deployPlan;
    }

    private void initialize(SchedulerDriver driver) throws InterruptedException {
        logger.info("Initializing...");
        initializeGlobals(driver);
        initializeRecoveryPlanManager();
        initializeDeploymentPlan();
        initializeResources();
        final List<PlanManager> planManagers = Arrays.asList(
                deployPlanManager,
                recoveryPlanManager);
        planCoordinator = new DefaultPlanCoordinator(planManagers, planScheduler);
        logger.info("Done initializing.");
    }

    private void initializeGlobals(SchedulerDriver driver) {
        logger.info("Initializing globals...");
        stateStore = new CuratorStateStore(serviceSpecification.getName(), zkConnectionString);
        taskFailureListener = new DefaultTaskFailureListener(stateStore);
        taskKiller = new DefaultTaskKiller(stateStore, taskFailureListener, driver);
        reconciler = new DefaultReconciler(stateStore);
        offerAccepter = new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(stateStore)));
        logger.info("Done initializing globals.");
    }

    private void initializeRecoveryPlanManager() {
        logger.info("Initializing recovery plan...");
        final RecoveryRequirementProvider recoveryRequirementProvider =
                new DefaultRecoveryRequirementProvider(new DefaultOfferRequirementProvider());
        final LaunchConstrainer constrainer =
                new TimedLaunchConstrainer(Duration.ofSeconds(DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES_SEC));

        recoveryPlanManager = new SimpleRecoveryPlanManager(
                stateStore,
                taskFailureListener,
                recoveryRequirementProvider,
                constrainer,
                new TimedFailureMonitor(Duration.ofSeconds(PERMANENT_FAILURE_DELAY_SEC)));
        logger.info("Done initializing recovery plan.");
    }

    private void initializeDeploymentPlan() {
        logger.info("Initializing deployment plan...");
        planScheduler = new DefaultPlanScheduler(offerAccepter, new OfferEvaluator(stateStore), taskKiller);

        try {
            deployPlan = new DefaultPlanFactory(stateStore).getPlan(serviceSpecification);
            logger.info("Generated plan: " + deployPlan);
            deployPlanManager = new DefaultPlanManager(deployPlan, new DefaultStrategyFactory());
        } catch (InvalidRequirementException e) {
            logger.error("Failed to generate deployPlan with exception: ", e);
            hardExit(SchedulerErrorCode.PLAN_CREATE_FAILURE);
        }
        logger.info("Done initializing deployment plan.");
    }

    private void initializeResources() throws InterruptedException {
        logger.info("Initializing resources...");
        Collection<Object> resources = new ArrayList<>();
        resources.add(new PlanResource(deployPlanManager));
        resources.add(new PlansResource(ImmutableMap.of(
                "deploy", deployPlanManager,
                "recovery", recoveryPlanManager)));
        resources.add(new StateResource(stateStore));
        resourcesQueue.put(resources);
        logger.info("Done initializing resources.");
    }

    private void logOffers(List<Protos.Offer> offers) {
        if (offers == null) {
            return;
        }

        logger.info(String.format("Received %d offers:", offers.size()));
        for (int i = 0; i < offers.size(); ++i) {
            // Offer protobuffers are very long. print each as a single line:
            logger.info(String.format("- Offer %d: %s", i + 1, TextFormat.shortDebugString(offers.get(i))));
        }
    }

    private void declineOffers(SchedulerDriver driver, List<Protos.OfferID> acceptedOffers, List<Protos.Offer> offers) {
        for (Protos.Offer offer : offers) {
            Protos.OfferID offerId = offer.getId();
            if (!acceptedOffers.contains(offerId)) {
                logger.info("Declining offer: " + offerId.getValue());
                driver.declineOffer(offerId);
            }
        }
    }

    private Optional<ResourceCleanerScheduler> getCleanerScheduler() {
        try {
            ResourceCleaner cleaner = new ResourceCleaner(stateStore);
            return Optional.of(new ResourceCleanerScheduler(cleaner, offerAccepter));
        } catch (Exception ex) {
            logger.error("Failed to construct ResourceCleaner", ex);
            return Optional.empty();
        }
    }

    private List<Protos.Offer> filterAcceptedOffers(List<Protos.Offer> offers, List<Protos.OfferID> acceptedOfferIds) {
        List<Protos.Offer> filteredOffers = new ArrayList<>();

        for (Protos.Offer offer : offers) {
            if (!offerAccepted(offer, acceptedOfferIds)) {
                filteredOffers.add(offer);
            }
        }

        return filteredOffers;
    }

    private boolean offerAccepted(Protos.Offer offer, List<Protos.OfferID> acceptedOfferIds) {
        for (Protos.OfferID acceptedOfferId : acceptedOfferIds) {
            if (acceptedOfferId.equals(offer.getId())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"DM_EXIT"})
    private void hardExit(SchedulerErrorCode errorCode) {
        System.exit(errorCode.ordinal());
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
        logger.info("Registered framework with frameworkId: " + frameworkId.getValue());
        try {
            initialize(driver);
        } catch (InterruptedException e) {
            logger.error("Initialization failed with exception: ", e);
            hardExit(SchedulerErrorCode.INITIALIZATION_FAILURE);
        }

        try {
            stateStore.storeFrameworkId(frameworkId);
        } catch (Exception e) {
            logger.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
        }

        reconciler.reconcile(driver);
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        logger.error("Re-registration implies we were unregistered.");
        hardExit(SchedulerErrorCode.RE_REGISTRATION);
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        executor.execute(() -> {
            logOffers(offers);

            // Task Reconciliation:
            // Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
            // Mesos have agreed upon the state of all Tasks of interest to the scheduler.
            // http://mesos.apache.org/documentation/latest/reconciliation/
            reconciler.reconcile(driver);
            if (!reconciler.isReconciled()) {
                logger.info("Accepting no offers: Reconciler is still in progress");
                return;
            }

            // Coordinate amongst all the plans via PlanCoordinator.
            final List<Protos.OfferID> acceptedOffers = new ArrayList<>();
            acceptedOffers.addAll(planCoordinator.processOffers(driver, offers));

            // Resource Cleaning:
            // A ResourceCleaner ensures that reserved Resources are not leaked.  It is possible that an Agent may
            // become inoperable for long enough that Tasks resident there were relocated.  However, this Agent may
            // return at a later point and begin offering reserved Resources again.  To ensure that these unexpected
            // reserved Resources are returned to the Mesos Cluster, the Resource Cleaner performs all necessary
            // UNRESERVE and DESTROY (in the case of persistent volumes) Operations.
            final Optional<ResourceCleanerScheduler> cleanerScheduler = getCleanerScheduler();
            if (cleanerScheduler.isPresent()) {
                acceptedOffers.addAll(cleanerScheduler.get().resourceOffers(driver, offers));
            }

            // Decline remaining offers.
            declineOffers(driver, acceptedOffers, offers);
        });
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
        logger.error("Rescinding offers is not supported.");
        hardExit(SchedulerErrorCode.OFFER_RESCINDED);
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                logger.info(String.format(
                        "Received status update for taskId=%s state=%s message='%s'",
                        status.getTaskId().getValue(),
                        status.getState().toString(),
                        status.getMessage()));

                // Store status, then pass status to PlanManager => Plan => Blocks
                try {
                    stateStore.storeStatus(status);
                    deployPlanManager.update(status);
                } catch (Exception e) {
                    logger.warn("Failed to update TaskStatus received from Mesos. "
                            + "This may be expected if Mesos sent stale status information: " + status, e);
                }
            }
        });
    }

    @Override
    public void frameworkMessage(
            SchedulerDriver driver,
            Protos.ExecutorID executorId,
            Protos.SlaveID slaveId,
            byte[] data) {
        logger.error("Received a Framework Message, but don't know how to process it");
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        logger.error("Disconnected from Master.");
        hardExit(SchedulerErrorCode.DISCONNECTED);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID agentId) {
        // TODO: Add recovery optimizations relevant to loss of an Agent.  TaskStatus updates are sufficient now.
        logger.warn("Agent lost: " + agentId);
    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, int status) {
        // TODO: Add recovery optimizations relevant to loss of an Executor.  TaskStatus updates are sufficient now.
        logger.warn(String.format("Lost Executor: %s on Agent: %s", executorId, slaveId));
    }

    @Override
    public void error(SchedulerDriver driver, String message) {
        logger.error("SchedulerDriver failed with message: " + message);
        hardExit(SchedulerErrorCode.ERROR);
    }
}
