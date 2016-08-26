package org.apache.mesos.scheduler;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.ResourceCleaner;
import org.apache.mesos.offer.ResourceCleanerScheduler;
import org.apache.mesos.reconciliation.DefaultReconciler;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.recovery.DefaultTaskFailureListener;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.state.PersistentOperationRecorder;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by gabriel on 8/25/16.
 */
public class DefaultScheduler implements Scheduler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ServiceSpecification serviceSpecification;

    private Reconciler reconciler;
    private StateStore stateStore;
    private TaskKiller taskKiller;
    private OfferAccepter offerAccepter;

    public DefaultScheduler(ServiceSpecification serviceSpecification) {
        this.serviceSpecification = serviceSpecification;
    }

    private void initialize() {
        logger.info("Initializing.");
        stateStore = new CuratorStateStore(serviceSpecification.getName());
        TaskFailureListener taskFailureListener = new DefaultTaskFailureListener(stateStore);
        taskKiller = new DefaultTaskKiller(taskFailureListener);
        reconciler = new DefaultReconciler();
        offerAccepter =
                new OfferAccepter(Arrays.asList(new PersistentOperationRecorder(stateStore)));
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
        logOffers(offers);
        reconciler.reconcile(driver);
        List<Protos.OfferID> acceptedOffers = new ArrayList<>();
        if (!reconciler.isReconciled()) {
            logger.info("Accepting no offers: Reconciler is still in progress");
        }

        logger.info(String.format("Accepted %d of %d offers: %s",
                acceptedOffers.size(), offers.size(), acceptedOffers));

        ResourceCleanerScheduler cleanerScheduler = getCleanerScheduler();
        if (cleanerScheduler != null) {
            acceptedOffers.addAll(getCleanerScheduler().resourceOffers(driver, offers));
        }
        declineOffers(driver, acceptedOffers, offers);

        taskKiller.process(driver);
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
        logger.error("Rescinding offers is not supported.");
        hardExit(SchedulerErrorCode.OFFER_RESCINDED);
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {

    }

    @Override
    public void frameworkMessage(SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID slaveId, byte[] data) {
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
