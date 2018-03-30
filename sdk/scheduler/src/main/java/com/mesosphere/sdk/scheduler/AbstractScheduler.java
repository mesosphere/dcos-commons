package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.framework.OfferProcessor;
import com.mesosphere.sdk.framework.ReviveManager;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Abstract main scheduler class that ties together the main pieces of a SDK Scheduler process.
 * Handles interaction with Mesos via an embedded {@link AbstractScheduler.MesosScheduler} object.
 */
public abstract class AbstractScheduler {

    private static final Logger LOGGER = LoggingUtils.getLogger(AbstractScheduler.class);

    protected final FrameworkStore frameworkStore;
    protected final ServiceSpec serviceSpec;
    protected final StateStore stateStore;
    protected final ConfigStore<ServiceSpec> configStore;
    protected final SchedulerConfig schedulerConfig;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Optional<PlanCustomizer> planCustomizer;

    // These are all (re)assigned when the scheduler has (re)registered:
    private ReviveManager reviveManager;
    private ExplicitReconciler reconciler;

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected AbstractScheduler(
            ServiceSpec serviceSpec,
            FrameworkStore frameworkStore,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            FrameworkConfig frameworkConfig,
            SchedulerConfig schedulerConfig,
            Optional<PlanCustomizer> planCustomizer) {
        this.serviceSpec = serviceSpec;
        this.frameworkStore = frameworkStore;
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.schedulerConfig = schedulerConfig;
        this.planCustomizer = planCustomizer;
    }

    /**
     * Returns the service spec for this service.
     */
    public ServiceSpec getServiceSpec() {
        return serviceSpec;
    }

    /**
     * Starts any internal threads to be used by the service.
     * Must be called after construction, once, in order for work to proceed.
     *
     * @return this
     */
    public AbstractScheduler start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("start() can only be called once");
        }

        if (planCustomizer.isPresent()) {
            for (PlanManager planManager : getPlanCoordinator().getPlanManagers()) {
                if (planManager.getPlan().isRecoveryPlan()) {
                    continue;
                }

                if (planManager.getPlan().isDeployPlan() && this instanceof UninstallScheduler) {
                    planManager.setPlan(planCustomizer.get().updateUninstallPlan(planManager.getPlan()));
                } else {
                    planManager.setPlan(planCustomizer.get().updatePlan(planManager.getPlan()));
                }
            }
        }

        return this;
    }

    /**
     * Returns the plans defined for this scheduler. Useful for scheduler tests.
     */
    @VisibleForTesting
    public Collection<Plan> getPlans() {
        return getPlanCoordinator().getPlanManagers().stream()
                .map(planManager -> planManager.getPlan())
                .collect(Collectors.toList());
    }

    public void registered(boolean reRegistered) {
        if (!reRegistered) {
            this.reviveManager = new ReviveManager();
            this.reconciler = new ExplicitReconciler(stateStore);
            registeredWithMesos();
        }
        // Explicit task reconciliation should be (re)started on all (re-)registrations.
        reconciler.start();
        reconciler.reconcile();
    }

    public void offers(Collection<Protos.Offer> offers) {
        /* Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
         * Mesos have agreed upon the state of all Tasks of interest to the scheduler.
         * See also: http://mesos.apache.org/documentation/latest/reconciliation/ */
        reconciler.reconcile();
        if (!reconciler.isReconciled()) {
            LOGGER.info("Not ready for offers: Waiting for task reconciliation to complete.");
            OfferProcessor.declineShort(offers);
            return;
        }

        // Get the current work
        Collection<Step> steps = getPlanCoordinator().getCandidates();

        // Revive previously suspended offers, if necessary
        Collection<Step> activeWorkSet = new HashSet<>(steps);
        Collection<Step> inProgressSteps = getInProgressSteps(getPlanCoordinator());
        if (!inProgressSteps.isEmpty()) {
            LOGGER.info("Steps in progress: {}",
                    inProgressSteps.stream().map(step -> step.getMessage()).collect(Collectors.toList()));
        }
        activeWorkSet.addAll(inProgressSteps);
        reviveManager.revive(activeWorkSet);

        LOGGER.info("Processing {} offer{} against {} step{}{}",
                offers.size(), offers.size() == 1 ? "" : "s",
                steps.size(), steps.size() == 1 ? "" : "s",
                offers.isEmpty() ? "" : ":");
        int i = 0;
        for (Protos.Offer offer : offers) {
            LOGGER.info("  {}: {}", ++i, TextFormat.shortDebugString(offer));
        }

        processOffers(offers, steps);
    }

    private static Set<Step> getInProgressSteps(PlanCoordinator planCoordinator) {
        return planCoordinator.getPlanManagers().stream()
                .map(planManager -> planManager.getPlan())
                .flatMap(plan -> plan.getChildren().stream())
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.isRunning())
                .collect(Collectors.toSet());
    }

    public void status(Protos.TaskStatus status) {
        try {
            processStatusUpdate(status);
            reconciler.update(status);
        } catch (Exception e) {
            if (e instanceof StateStoreException && ((StateStoreException) e).getReason() == Reason.NOT_FOUND) {
                LOGGER.info("Status for unknown task. This may be expected if Mesos sent stale status information: "
                        + TextFormat.shortDebugString(status), e);
                return;
            }
            LOGGER.warn("Failed to update TaskStatus received from Mesos: " + TextFormat.shortDebugString(status), e);
        }
    }

    /**
     * Returns the {@link PlanCoordinator}.
     */
    public abstract PlanCoordinator getPlanCoordinator();

    /**
     * Returns a list of API resources to be served by the scheduler to the local cluster.
     */
    public abstract Collection<Object> getResources();

    /**
     * Invoked when the framework has registered (or re-registered) with Mesos.
     */
    protected abstract void registeredWithMesos();

    /**
     * Invoked when Mesos has provided offers to be evaluated.
     *
     * @param offers zero or more offers (zero may periodically be passed to 'turn the crank' on other processing)
     * @param steps candidate steps which had been returned by the {@link PlanCoordinator}
     */
    protected abstract void processOffers(Collection<Protos.Offer> offers, Collection<Step> steps);

    /**
     * Invoked when Mesos has provided a task status to be processed.
     *
     * @param status the task status, which may be for a task which no longer exists
     */
    protected abstract void processStatusUpdate(Protos.TaskStatus status) throws Exception;
}
