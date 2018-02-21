package com.mesosphere.sdk.scheduler.framework;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.evaluate.placement.IsLocalRegionRule;
import com.mesosphere.sdk.scheduler.Driver;
import com.mesosphere.sdk.scheduler.Metrics;
import com.mesosphere.sdk.scheduler.SchedulerErrorCode;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.framework.MesosEventClient.StatusResponse;
import com.mesosphere.sdk.state.FrameworkStore;

/**
 * Implementation of Mesos' {@link Scheduler} interface.
 * Received messages are forwarded to the provided {@link MesosEventClient} instance.
 */
public class FrameworkScheduler implements Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkScheduler.class);

    // Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
    // master re-election. Avoid performing initialization multiple times, which would cause queues to be stuck.
    private final AtomicBoolean isRegisterStarted = new AtomicBoolean(false);

    private final FrameworkStore frameworkStore;
    private final MesosEventClient mesosEventClient;
    private final OfferProcessor offerProcessor;

    // Tracks whether apiServer has entered a started state. We avoid launching tasks until after the API server has
    // started, because when tasks launch they typically require access to ArtifactResource for config templates.
    private final AtomicBoolean apiServerStarted = new AtomicBoolean(false);

    public FrameworkScheduler(FrameworkStore frameworkStore, MesosEventClient mesosEventClient) {
        this.frameworkStore = frameworkStore;
        this.mesosEventClient = mesosEventClient;
        this.offerProcessor = new OfferProcessor(mesosEventClient);
    }

    public void markApiServerStarted() {
        apiServerStarted.set(true);
    }

    /**
     * All offers must have been presented to resourceOffers() before calling this.  This call will block until all
     * offers have been processed.
     *
     * @throws InterruptedException if waiting for offers to be processed is interrupted
     * @throws IllegalStateException if offers were not processed in a reasonable amount of time
     */
    @VisibleForTesting
    public void awaitOffersProcessed() throws InterruptedException {
        offerProcessor.awaitOffersProcessed();
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
        if (isRegisterStarted.getAndSet(true)) {
            // This may occur as the result of a master election.
            LOGGER.info("Already registered, calling reregistered()");
            reregistered(driver, masterInfo);
            return;
        }

        LOGGER.info("Registered framework with frameworkId: {}", frameworkId.getValue());
        try {
            frameworkStore.storeFrameworkId(frameworkId);
        } catch (Exception e) {
            LOGGER.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            SchedulerUtils.hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
        }

        updateStaticData(driver, masterInfo);
        mesosEventClient.registered(false);

        offerProcessor.start();
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.info("Re-registered with master: {}", TextFormat.shortDebugString(masterInfo));
        updateStaticData(driver, masterInfo);
        mesosEventClient.registered(true);
    }

    private static void updateStaticData(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        Driver.setDriver(driver);
        if (masterInfo.hasDomain()) {
            IsLocalRegionRule.setLocalDomain(masterInfo.getDomain());
        }
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        Metrics.incrementReceivedOffers(offers.size());

        if (!apiServerStarted.get()) {
            LOGGER.info("Declining {} offer{}: Waiting for API Server to start.",
                    offers.size(), offers.size() == 1 ? "" : "s");
            OfferProcessor.declineShort(offers);
            return;
        }

        offerProcessor.enqueue(offers);
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        LOGGER.info("Received status update for taskId={} state={} message={} protobuf={}",
                status.getTaskId().getValue(),
                status.getState().toString(),
                status.getMessage(),
                TextFormat.shortDebugString(status));
        Metrics.record(status);
        StatusResponse response = mesosEventClient.status(status);
        TaskKiller.update(status);
        switch (response.result) {
        case UNKNOWN_TASK:
            TaskKiller.killTask(status.getTaskId());
            break;
        case PROCESSED:
            // No-op
            break;
        }
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
        LOGGER.info("Rescinding offer: {}", offerId.getValue());
        offerProcessor.dequeue(offerId);
    }

    @Override
    public void frameworkMessage(
            SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID agentId, byte[] data) {
        LOGGER.error("Received a {} byte Framework Message from Executor {}, but don't know how to process it",
                data.length, executorId.getValue());
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        LOGGER.error("Disconnected from Master, shutting down.");
        SchedulerUtils.hardExit(SchedulerErrorCode.DISCONNECTED);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID agentId) {
        // TODO: Add recovery optimizations relevant to loss of an Agent.  TaskStatus updates are sufficient now.
        LOGGER.warn("Agent lost: {}", agentId.getValue());
    }

    @Override
    public void executorLost(
            SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID agentId, int status) {
        // TODO: Add recovery optimizations relevant to loss of an Executor.  TaskStatus updates are sufficient now.
        LOGGER.warn("Lost Executor: {} on Agent: {}", executorId.getValue(), agentId.getValue());
    }

    @Override
    public void error(SchedulerDriver driver, String message) {
        LOGGER.error("SchedulerDriver returned an error, shutting down: {}", message);
        SchedulerUtils.hardExit(SchedulerErrorCode.ERROR);
    }
}