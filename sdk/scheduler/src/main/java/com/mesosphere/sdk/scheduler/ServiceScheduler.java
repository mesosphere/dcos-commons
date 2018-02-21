package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.framework.MesosEventClient;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.Persister;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Abstract main scheduler class that ties together the main pieces of a Service.
 */
public abstract class ServiceScheduler implements MesosEventClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceScheduler.class);

    protected final FrameworkStore frameworkStore;
    protected final StateStore stateStore;
    protected final SchedulerConfig schedulerConfig;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Optional<PlanCustomizer> planCustomizer;

    // These are all (re)assigned when the scheduler has (re)registered:
    private ReviveManager reviveManager;
    private Reconciler reconciler;

    /**
     * Executor for handling TaskStatus updates in {@link Scheduler#statusUpdate(SchedulerDriver, Protos.TaskStatus)}.
     */
    protected final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new AbstractScheduler given a {@link StateStore}.
     */
    protected ServiceScheduler(
            FrameworkStore frameworkStore,
            StateStore stateStore,
            SchedulerConfig schedulerConfig,
            Optional<PlanCustomizer> planCustomizer) {
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
     * Returns the framework ID currently in persistent storage, or an empty {@link Optional} if no framework ID had
     * been stored yet.
     *
     * @throws StateStoreException if storage access fails
     */
    Optional<Protos.FrameworkID> fetchFrameworkId() {
        return frameworkStore.fetchFrameworkId();
    }

    /**
     * Returns the underlying {@link Persister} being used to keep track of state/configs.
     */
    Persister getPersister() {
        return frameworkStore.getPersister();
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
    public void registered(boolean reRegistered) {
        if (!reRegistered) {
            this.reviveManager = new ReviveManager();
            this.reconciler = new Reconciler(stateStore);
            registeredWithMesos();
        }
        // Task reconciliation should be (re)started on all (re-)registrations.
        reconciler.start();
        reconciler.reconcile();
    }

    @Override
    public OfferResponse offers(List<Protos.Offer> offers) {
        // Task Reconciliation:
        // Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
        // Mesos have agreed upon the state of all Tasks of interest to the scheduler.
        // http://mesos.apache.org/documentation/latest/reconciliation/
        reconciler.reconcile();
        if (!reconciler.isReconciled()) {
            LOGGER.info("Not ready for offers: Waiting for task reconciliation to complete.");
            return OfferResponse.notReady(offers);
        }

        // Get the current work
        Collection<Step> steps = getPlanCoordinator().getCandidates();

        // Revive previously suspended offers, if necessary
        Collection<Step> activeWorkSet = new HashSet<>(steps);
        Collection<Step> inProgressSteps = getInProgressSteps(getPlanCoordinator());
        LOGGER.info("InProgress Steps: {}",
                inProgressSteps.stream()
                        .map(step -> step.getMessage())
                        .collect(Collectors.toList()));
        activeWorkSet.addAll(inProgressSteps);
        reviveManager.revive(activeWorkSet);

        LOGGER.info("Processing {} offer{} against {} step{}:",
                offers.size(), offers.size() == 1 ? "" : "s",
                steps.size(), steps.size() == 1 ? "" : "s");
        for (int i = 0; i < offers.size(); ++i) {
            LOGGER.info("  {}: {}", i + 1, TextFormat.shortDebugString(offers.get(i)));
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
            // TODO address this throwing when the status is for an unknown task. This will be much more common now!
            processStatusUpdate(status);
            reconciler.update(status);
        } catch (Exception e) {
            LOGGER.warn("Failed to update TaskStatus received from Mesos. "
                    + "This may be expected if Mesos sent stale status information: " + status, e);
        }
        return StatusResponse.forStatus(stateStore, status);
    }

    protected abstract void registeredWithMesos();
    protected abstract List<Protos.Offer> processOffers(List<Protos.Offer> offers, Collection<Step> steps);
    protected abstract void processStatusUpdate(Protos.TaskStatus status) throws Exception;

    /**
     * Returns a list of API resources to be served by the scheduler to the local cluster.
     */
    // TODO one per framework, not per service
    public abstract Collection<Object> getResources();

    /**
     * Returns the {@link PlanCoordinator}.
     */
    protected abstract PlanCoordinator getPlanCoordinator();
}
