package com.mesosphere.sdk.framework;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.evaluate.placement.IsLocalRegionRule;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.Driver;
import com.mesosphere.sdk.scheduler.Metrics;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerErrorCode;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.scheduler.TaskCleaner;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;

/**
 * Implementation of Mesos' {@link Scheduler} interface. There should only be one of these per Scheduler process.
 * Received messages are forwarded to the provided {@link MesosEventClient} instance.
 */
public class FrameworkScheduler implements Scheduler {

    private static final Logger LOGGER = LoggingUtils.getLogger(FrameworkScheduler.class);

    /**
     * Mesos may call registered() multiple times in the lifespan of a Scheduler process, specifically when there's
     * master re-election. Avoid performing initialization multiple times, which would cause queues to be stuck.
     */
    private final AtomicBoolean isRegisterStarted = new AtomicBoolean(false);

    /**
     * Tracks whether the API Server has entered a started state. We avoid launching tasks until after the API server is
     * started, because when tasks launch they typically require access to ArtifactResource for config templates.
     */
    private final AtomicBoolean readyToAcceptOffers = new AtomicBoolean(false);

    private final Set<String> frameworkRolesWhitelist;
    private final FrameworkStore frameworkStore;
    private final StateStore stateStore;
    private final AbstractScheduler abstractScheduler;
    private final OfferProcessor offerProcessor;

    private TaskCleaner taskCleaner;
    private boolean multithreaded = true;

    public FrameworkScheduler(
            Set<String> frameworkRolesWhitelist,
            SchedulerConfig schedulerConfig,
            FrameworkStore frameworkStore,
            StateStore stateStore,
            AbstractScheduler abstractScheduler) {
        this(
                frameworkRolesWhitelist,
                frameworkStore,
                stateStore,
                abstractScheduler,
                new OfferProcessor(abstractScheduler, stateStore));
    }

    @VisibleForTesting
    FrameworkScheduler(
            Set<String> frameworkRolesWhitelist,
            FrameworkStore frameworkStore,
            StateStore stateStore,
            AbstractScheduler abstractScheduler,
            OfferProcessor offerProcessor) {
        this.frameworkRolesWhitelist = frameworkRolesWhitelist;
        this.frameworkStore = frameworkStore;
        this.stateStore = stateStore;
        this.abstractScheduler = abstractScheduler;
        this.offerProcessor = offerProcessor;
    }

    /**
     * Notifies this instance that the API server has been initialized. All offers are declined until this is called.
     *
     * @return {@code this}
     */
    public FrameworkScheduler setReadyToAcceptOffers() {
        readyToAcceptOffers.set(true);
        return this;
    }

    /**
     * Disables multithreading for tests. For this to take effect, it must be invoked before the framework has
     * registered.
     *
     * @return {@code this}
     */
    @VisibleForTesting
    public FrameworkScheduler disableThreading() {
        offerProcessor.disableThreading();
        this.multithreaded = false;
        return this;
    }

    @Override
    public void registered(SchedulerDriver driver, Protos.FrameworkID frameworkId, Protos.MasterInfo masterInfo) {
        if (isRegisterStarted.getAndSet(true)) {
            // This may occur as the result of a master election.
            LOGGER.info("Already registered, calling reregistered()");
            reregistered(driver, masterInfo);
            return;
        }

        Driver.setDriver(driver);
        abstractScheduler.registeredWithMesos();

        LOGGER.info("Registered framework with frameworkId: {}", frameworkId.getValue());
        this.taskCleaner = new TaskCleaner(stateStore, multithreaded);

        try {
            frameworkStore.storeFrameworkId(frameworkId);
        } catch (Exception e) {
            LOGGER.error(String.format(
                    "Unable to store registered framework ID '%s'", frameworkId.getValue()), e);
            SchedulerUtils.hardExit(SchedulerErrorCode.REGISTRATION_FAILURE);
        }

        postRegister(driver, masterInfo);

        offerProcessor.start();
    }

    @Override
    public void reregistered(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        LOGGER.info("Re-registered with master: {}", TextFormat.shortDebugString(masterInfo));
        Driver.setDriver(driver);
        postRegister(driver, masterInfo);
    }

    private void postRegister(SchedulerDriver driver, Protos.MasterInfo masterInfo) {
        // Task reconciliation should be (re)started on all (re-)registrations.
        Reconciler reconciler = offerProcessor.getReconciler();
        reconciler.start();
        reconciler.reconcile();
        if (masterInfo.hasDomain()) {
            IsLocalRegionRule.setLocalDomain(masterInfo.getDomain());
        }
    }

    @Override
    public void resourceOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        Metrics.incrementReceivedOffers(offers.size());

        if (!readyToAcceptOffers.get()) {
            LOGGER.info("Declining {} offer{}: Waiting for API Server to start.",
                    offers.size(), offers.size() == 1 ? "" : "s");
            OfferProcessor.declineShort(offers);
            return;
        }

        // Filter any bad resources from the offers before they even enter processing.
        offerProcessor.enqueue(offers.stream()
                .map(offer -> filterBadResources(offer))
                .collect(Collectors.toList()));
    }

    /**
     * Before we forward the offers to the processor queue, lets filter out resources that don't belong to us.
     * Resources can look like one of the following:
     * 1. Dynamic against our-role or refined-role/our-role (belongs to us)
     * 2. Static against refined-role (we can reserve against it)
     * 3. Dynamic against refined-role (DOESN'T belong to us at all! Likely created by Marathon)
     * We specifically want to ensure that any resources from case 3 are not visible to our service. They are
     * effectively a quirk of how Mesos behaves with roles, and ideally we wouldn't see these resources at all.
     * So what we do here is filter out all the resources which are dynamic AND which lack one of our expected
     * resource roles. To be extra safe, we also check that any dynamic resources have a resource_id label, which
     * hints that the resources were indeed created by us.
     *
     * @param offer the original offer received by mesos
     * @return a copy of that offer with any resources which don't belong to us filtered out, or the original offer if
     *         no changes were needed
     */
    private Protos.Offer filterBadResources(Protos.Offer offer) {
        Collection<Protos.Resource> goodResources = new ArrayList<>();
        Collection<Protos.Resource> badResources = new ArrayList<>();
        for (Protos.Resource resource : offer.getResourcesList()) {
            if (ResourceUtils.isProcessable(resource, frameworkRolesWhitelist)) {
                goodResources.add(resource);
            } else {
                badResources.add(resource);
            }
        }
        if (badResources.isEmpty()) {
            // All resources are good. Just return the original offer.
            return offer;
        }

        // Build a new offer which only contains the good resources. Log the bad resources.
        LOGGER.info("Filtered {} resources from offer {}:", badResources.size(), offer.getId().getValue());
        for (Protos.Resource badResource : badResources) {
            LOGGER.info("  {}", TextFormat.shortDebugString(badResource));
        }
        return offer.toBuilder()
                .clearResources()
                .addAllResources(goodResources)
                .build();
    }

    @Override
    public void statusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        LOGGER.info("Received status update for taskId={} state={} message={} protobuf={}",
                status.getTaskId().getValue(),
                status.getState().toString(),
                status.getMessage(),
                TextFormat.shortDebugString(status));
        try {
            abstractScheduler.processStatusUpdate(status);
        } catch (Exception e) {
            LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                    + "This may be expected if Mesos sent stale status information: " + status, e);
        }

        offerProcessor.getReconciler().update(status);
        TaskKiller.update(status);
        Metrics.record(status);
        taskCleaner.statusUpdate(status);
    }

    @Override
    public void offerRescinded(SchedulerDriver driver, Protos.OfferID offerId) {
        LOGGER.info("Rescinding offer: {}", offerId.getValue());
        offerProcessor.dequeue(offerId);
    }

    @Override
    public void frameworkMessage(
            SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID agentId, byte[] data) {
        LOGGER.error("Received unsupported {} byte Framework Message from Executor {} on Agent {}",
                data.length, executorId.getValue(), agentId.getValue());
    }

    @Override
    public void disconnected(SchedulerDriver driver) {
        LOGGER.error("Disconnected from Master, shutting down.");
        SchedulerUtils.hardExit(SchedulerErrorCode.DISCONNECTED);
    }

    @Override
    public void slaveLost(SchedulerDriver driver, Protos.SlaveID agentId) {
        LOGGER.warn("Agent lost: {}", agentId.getValue());
    }

    @Override
    public void executorLost(
            SchedulerDriver driver, Protos.ExecutorID executorId, Protos.SlaveID agentId, int status) {
        LOGGER.warn("Lost Executor: {} on Agent: {}", executorId.getValue(), agentId.getValue());
    }

    @Override
    public void error(SchedulerDriver driver, String message) {
        LOGGER.error("SchedulerDriver returned an error, shutting down: {}", message);
        SchedulerUtils.hardExit(SchedulerErrorCode.ERROR);
    }
}
