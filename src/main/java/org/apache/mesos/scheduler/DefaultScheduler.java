package org.apache.mesos.scheduler;

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
import org.apache.mesos.scheduler.recovery.*;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.constrain.TimedLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.TimedFailureMonitor;
import org.apache.mesos.specification.DefaultPlanSpecificationFactory;
import org.apache.mesos.specification.PlanSpecification;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.PersistentOperationRecorder;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultScheduler implements Scheduler {
    private static final Integer DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES = 600;
    private static final Integer PERMANENT_FAILURE_DELAY_SEC = 1200;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ServiceSpecification serviceSpecification;
    private final String zkConnectionString;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final AtomicReference<RecoveryStatus> recoveryStatusRef;

    private Reconciler reconciler;
    private StateStore stateStore;
    private TaskFailureListener taskFailureListener;
    private TaskKiller taskKiller;
    private OfferAccepter offerAccepter;
    private Plan plan;
    private PlanManager planManager;
    private DefaultBlockScheduler blockScheduler;
    private DefaultRecoveryScheduler recoveryScheduler;

    public DefaultScheduler(ServiceSpecification serviceSpecification) {
        this(serviceSpecification, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    public DefaultScheduler(ServiceSpecification serviceSpecification, String zkConnectionString) {
        this.serviceSpecification = serviceSpecification;
        this.zkConnectionString = zkConnectionString;
        recoveryStatusRef = new AtomicReference<>(new RecoveryStatus(Collections.emptyList(), Collections.emptyList()));
    }

    void awaitTermination() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    Plan getPlan() {
        return plan;
    }

    private void initialize() {
        logger.info("Initializing.");
        initializeGlobals();
        initializeRecoveryScheduler();
        initializeDeploymentPlan();
    }

    private void initializeGlobals() {
        stateStore = new CuratorStateStore(serviceSpecification.getName(), zkConnectionString);
        taskFailureListener = new DefaultTaskFailureListener(stateStore);
        taskKiller = new DefaultTaskKiller(stateStore, taskFailureListener);
        reconciler = new DefaultReconciler();
        offerAccepter =
                new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(stateStore)));

    }

    private void initializeDeploymentPlan() {
        blockScheduler = new DefaultBlockScheduler(offerAccepter, taskKiller);
        PlanSpecification planSpecification =
                new DefaultPlanSpecificationFactory().getPlanSpecification(serviceSpecification);

        try {
            plan = new DefaultPlanFactory(stateStore).getPlan(planSpecification);
            logger.info("Generated plan: " + plan);
            planManager = new DefaultPlanManager(plan, new DefaultStrategyFactory());
        } catch (InvalidRequirementException e) {
            logger.error("Failed to generate plan with exception: ", e);
            hardExit(SchedulerErrorCode.PLAN_CREATE_FAILURE);
        }

    }

    private void initializeRecoveryScheduler() {
        RecoveryRequirementProvider recoveryRequirementProvider =
                new DefaultRecoveryRequirementProvider(new DefaultOfferRequirementProvider());
        LaunchConstrainer constrainer =
                new TimedLaunchConstrainer(Duration.ofSeconds(DELAY_BETWEEN_DESTRUCTIVE_RECOVERIES));
        recoveryScheduler = new DefaultRecoveryScheduler(
                stateStore,
                taskFailureListener,
                recoveryRequirementProvider,
                offerAccepter,
                constrainer,
                new TimedFailureMonitor(Duration.ofSeconds(PERMANENT_FAILURE_DELAY_SEC)),
                recoveryStatusRef);

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

    private ResourceCleanerScheduler getCleanerScheduler() {
        try {
            ResourceCleaner cleaner = new ResourceCleaner(stateStore.getExpectedResources());
            return new ResourceCleanerScheduler(cleaner, offerAccepter);
        } catch (Exception ex) {
            logger.error("Failed to construct ResourceCleaner", ex);
            return null;
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
        for (Protos.OfferID acceptedOfferId: acceptedOfferIds) {
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
        initialize();

        try {
            stateStore.storeFrameworkId(frameworkId);
        } catch (Exception e) {
            logger.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
        }
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        logger.error("Re-registration implies we were unregistered.");
        hardExit(SchedulerErrorCode.RE_REGISTRATION);
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                logOffers(offers);
                reconciler.reconcile(driver);
                List<Protos.OfferID> acceptedOffers = new ArrayList<>();
                if (!reconciler.isReconciled()) {
                    logger.info("Accepting no offers: Reconciler is still in progress");
                    return;
                }

                Block block = planManager.getCurrentBlock();
                if (block != null) {
                    acceptedOffers = blockScheduler.resourceOffers(driver, offers, block);
                    logger.info(String.format("Accepted %d of %d offers: %s",
                            acceptedOffers.size(), offers.size(), acceptedOffers));
                }

                List<Protos.Offer> unacceptedOffers = filterAcceptedOffers(offers, acceptedOffers);
                try {
                    acceptedOffers.addAll(recoveryScheduler.resourceOffers(driver, unacceptedOffers, block));
                } catch (Exception e) {
                    logger.error("Error repairing block: " + block + " Reason: " + e);
                }

                ResourceCleanerScheduler cleanerScheduler = getCleanerScheduler();
                if (cleanerScheduler != null) {
                    acceptedOffers.addAll(getCleanerScheduler().resourceOffers(driver, offers));
                }
                declineOffers(driver, acceptedOffers, offers);

                taskKiller.process(driver);
            }
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
                    planManager.update(status);
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
        hardExit(SchedulerErrorCode.FRAMEWORK_MESSAGE);
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        logger.error("Disconnected from Master.");
        hardExit(SchedulerErrorCode.DISCONNECTED);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID slaveId) {
        logger.warn("Slave lost: " + slaveId);
    }

    @Override
    public void executorLost(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, int status) {
        logger.warn(String.format("Lost Executor: %s on Agent: %s", executorId, slaveId));
    }

    @Override
    public void error(SchedulerDriver driver, String message) {
        logger.error("SchedulerDriver failed with message: " + message);
        hardExit(SchedulerErrorCode.ERROR);
    }
}
