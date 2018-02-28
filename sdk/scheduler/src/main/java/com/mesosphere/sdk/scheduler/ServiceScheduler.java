package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Abstract main scheduler class that ties together the main pieces of a Service.
 */
public abstract class ServiceScheduler implements MesosEventClient {

    private final Logger logger;

    private final String serviceName;
    protected final FrameworkStore frameworkStore;
    protected final StateStore stateStore;
    protected final SchedulerConfig schedulerConfig;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Optional<PlanCustomizer> planCustomizer;

    // These are all (re)assigned when the scheduler has (re)registered:
    private ReviveManager reviveManager;
    private Reconciler reconciler;

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected ServiceScheduler(
            String serviceName,
            FrameworkStore frameworkStore,
            StateStore stateStore,
            SchedulerConfig schedulerConfig,
            Optional<PlanCustomizer> planCustomizer) {
        this.logger = LoggingUtils.getLogger(getClass(), serviceName);
        this.serviceName = serviceName;
        this.frameworkStore = frameworkStore;
        this.stateStore = stateStore;
        this.schedulerConfig = schedulerConfig;
        this.planCustomizer = planCustomizer;
    }

    /**
     * Starts any internal threads to be used by the service.
     * Must be called after construction, once, in order for work to proceed.
     *
     * @return this
     */
    public ServiceScheduler start() {
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
     * Returns the underlying {@link Persister} being used to keep track of state/configs.
     */
    public Persister getPersister() {
        return stateStore.getPersister();
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

    @Override
    public void register(boolean reRegistered) {
        if (!reRegistered) {
            this.reviveManager = new ReviveManager(serviceName);
            this.reconciler = new Reconciler(stateStore);
            registeredWithMesos();
        }
        // Task reconciliation should be (re)started on all (re-)registrations.
        reconciler.start();
        reconciler.reconcile();
    }

    @Override
    public OfferResponse offers(List<Protos.Offer> offers) {
        /* Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
         * Mesos have agreed upon the state of all Tasks of interest to the scheduler.
         * See also: http://mesos.apache.org/documentation/latest/reconciliation/ */
        reconciler.reconcile();
        if (!reconciler.isReconciled()) {
            logger.info("Not ready for offers: Waiting for task reconciliation to complete.");
            return OfferResponse.notReady(offers);
        }

        // Get the current work
        Collection<Step> steps = getPlanCoordinator().getCandidates();

        // Revive previously suspended offers, if necessary
        Collection<Step> activeWorkSet = new HashSet<>(steps);
        Collection<Step> inProgressSteps = getInProgressSteps(getPlanCoordinator());
        logger.info("InProgress Steps: {}",
                inProgressSteps.stream()
                        .map(step -> step.getMessage())
                        .collect(Collectors.toList()));
        activeWorkSet.addAll(inProgressSteps);
        reviveManager.revive(activeWorkSet);

        logger.info("Processing {} offer{} against {} step{}:",
                offers.size(), offers.size() == 1 ? "" : "s",
                steps.size(), steps.size() == 1 ? "" : "s");
        for (int i = 0; i < offers.size(); ++i) {
            logger.info("  {}: {}", i + 1, TextFormat.shortDebugString(offers.get(i)));
        }

        return OfferResponse.processed(processOffers(offers, steps));
    }

    private static Set<Step> getInProgressSteps(PlanCoordinator planCoordinator) {
        return planCoordinator.getPlanManagers().stream()
                .map(planManager -> planManager.getPlan())
                .flatMap(plan -> plan.getChildren().stream())
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.isRunning())
                .collect(Collectors.toSet());
    }

    @Override
    public StatusResponse status(Protos.TaskStatus status) {
        try {
            processStatusUpdate(status);
            reconciler.update(status);
        } catch (Exception e) {
            if (e instanceof StateStoreException && ((StateStoreException) e).getReason() == Reason.NOT_FOUND) {
                logger.info("Status for unknown task. This may be expected if Mesos sent stale status information: "
                        + TextFormat.shortDebugString(status), e);
                return StatusResponse.unknownTask();
            }
            logger.warn("Failed to update TaskStatus received from Mesos: " + TextFormat.shortDebugString(status), e);
        }
        return StatusResponse.processed();
    }

    /**
     * Returns the {@link StateStore}.
     */
    public StateStore getStateStore() {
        return stateStore;
    }

    /**
     * Returns the {@link PlanCoordinator}.
     */
    public abstract PlanCoordinator getPlanCoordinator();

    /**
     * Returns the {@link ConfigStore}.
     */
    public abstract ConfigStore<ServiceSpec> getConfigStore();

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
    protected abstract List<Protos.Offer> processOffers(List<Protos.Offer> offers, Collection<Step> steps);

    /**
     * Invoked when Mesos has provided a task status to be processed.
     *
     * @param status the task status, which may be for a task which no longer exists or is otherwise unrelated to the
     *               service
     */
    protected abstract void processStatusUpdate(Protos.TaskStatus status) throws Exception;
}
