package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanCustomizer;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.uninstall.UninstallScheduler;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract main scheduler class that ties together the main pieces of a Service.
 */
public abstract class AbstractScheduler implements MesosEventClient {

    private final Logger logger;
    private final Optional<String> namespace;
    private final Collection<Step> candidateSteps;

    protected final ServiceSpec serviceSpec;
    protected final SchedulerConfig schedulerConfig;
    protected final StateStore stateStore;
    protected final PlanCoordinator planCoordinator;

    public Optional<PlanCustomizer> getPlanCustomizer() {
        return planCustomizer;
    }

    protected final Optional<PlanCustomizer> planCustomizer;

    // These are all assigned when the scheduler has registered:
    protected WorkSetTracker workSetTracker;
    private ExplicitReconciler reconciler;

    protected AbstractScheduler(
            ServiceSpec serviceSpec,
            SchedulerConfig schedulerConfig,
            StateStore stateStore,
            PlanCoordinator planCoordinator,
            Optional<PlanCustomizer> planCustomizer,
            Optional<String> namespace) {
        this.logger = LoggingUtils.getLogger(AbstractScheduler.class, namespace);
        this.namespace = namespace;
        this.candidateSteps = new ArrayList<>();
        this.serviceSpec = serviceSpec;
        this.schedulerConfig = schedulerConfig;
        this.stateStore = stateStore;
        this.planCustomizer = planCustomizer;
        this.planCoordinator = planCoordinator;
    }

    protected void customizePlans() {
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
    }

    /**
     * Returns the service spec for this service.
     */
    public ServiceSpec getServiceSpec() {
        return serviceSpec;
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
            this.workSetTracker = new WorkSetTracker(namespace);
            this.reconciler = new ExplicitReconciler(stateStore, namespace, schedulerConfig);
            registeredWithMesos();
        }
        // Explicit task reconciliation should be (re)started on all (re-)registrations.
        reconciler.start();
        reconciler.reconcile();
    }

    @Override
    public ClientStatusResponse getClientStatus() {
        // Get the current work, and save it for the following offers() call.
        candidateSteps.clear();
        candidateSteps.addAll(getPlanCoordinator().getCandidates());

        // Update workSetTracker: detect any new work that triggers the need to revive offers
        Collection<Step> activeWorkSet = new HashSet<>(candidateSteps);
        Collection<Step> inProgressSteps = getInProgressSteps(getPlanCoordinator());
        if (!inProgressSteps.isEmpty()) {
            logger.info("Steps in progress: {}",
                    inProgressSteps.stream().map(step -> step.getMessage()).collect(Collectors.toList()));
        }
        activeWorkSet.addAll(inProgressSteps);

        try {
            workSetTracker.updateWorkSet(activeWorkSet);
        } catch (NullPointerException e) {
            logger.warn("workset tracker is uninitialized, scheduler has his a null reference and is exiting.");

        }

        return getStatus();
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
    public OfferResponse offers(Collection<Protos.Offer> offers) {
        /* Task Reconciliation must complete before any Tasks may be launched.  It ensures that a Scheduler and
         * Mesos have agreed upon the state of all Tasks of interest to the scheduler.
         * See also: http://mesos.apache.org/documentation/latest/reconciliation/ */
        reconciler.reconcile();
        if (!reconciler.isReconciled()) {
            logger.info("Not ready for offers: Waiting for task reconciliation to complete.");
            return OfferResponse.notReady(Collections.emptyList());
        }

        if (!offers.isEmpty()) {
            logger.info("Processing {} offer{} against {} step{}:",
                    offers.size(), offers.size() == 1 ? "" : "s",
                    candidateSteps.size(), candidateSteps.size() == 1 ? "" : "s");
            int i = 0;
            for (Protos.Offer offer : offers) {
                logger.info("  {}: {}", ++i, TextFormat.shortDebugString(offer));
            }
        }

        return processOffers(offers, candidateSteps);
    }

    @Override
    public TaskStatusResponse taskStatus(Protos.TaskStatus status) {
        try {
            processStatusUpdate(status);
            reconciler.update(status);
        } catch (Exception e) {
            if (e instanceof StateStoreException && ((StateStoreException) e).getReason() == Reason.NOT_FOUND) {
                logger.info("Status for unknown task. This may be expected if Mesos sent stale status information: "
                        + TextFormat.shortDebugString(status), e);
                return TaskStatusResponse.unknownTask();
            }
            logger.warn("Failed to update TaskStatus received from Mesos: " + TextFormat.shortDebugString(status), e);
        }
        return TaskStatusResponse.processed();
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
    public PlanCoordinator getPlanCoordinator() {
        return planCoordinator;
    }

    /**
     * Returns the custom endpoints, or an empty map if there are none.
     */
    public abstract Map<String, EndpointProducer> getCustomEndpoints();

    /**
     * Returns the {@link ConfigStore}.
     */
    public abstract ConfigStore<ServiceSpec> getConfigStore();

    /**
     * Invoked when the framework has registered (or re-registered) with Mesos.
     */
    protected abstract void registeredWithMesos();

    /**
     * Invoked to get the underlying scheduler object's status.
     */
    protected abstract ClientStatusResponse getStatus();

    /**
     * Invoked when Mesos has provided offers to be evaluated.
     *
     * @param offers zero or more offers (zero may periodically be passed to 'turn the crank' on other processing)
     * @param steps candidate steps which had been returned by the {@link PlanCoordinator}
     */
    protected abstract OfferResponse processOffers(Collection<Protos.Offer> offers, Collection<Step> steps);

    /**
     * Invoked when Mesos has provided a task status to be processed.
     *
     * @param status the task status, which may be for a task which no longer exists or is otherwise unrelated to the
     *               service
     */
    protected abstract void processStatusUpdate(Protos.TaskStatus status) throws Exception;
}
