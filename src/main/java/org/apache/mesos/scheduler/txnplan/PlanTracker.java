package org.apache.mesos.scheduler.txnplan;

import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * This thing runs the Plans for you. It's definitely not serializable, but its serializable state
 * should be encapsulated by the PlanStatus.
 *
 * It's highly likely that PlanListener is buggy!!!
 *
 * TODO needs a "kill()" function to terminate early
 * TODO probably actually needs to be 2--hard and soft kills
 * Created by dgrnbrg on 6/21/16.
 */
public class PlanTracker {
    private static final Logger logger = LoggerFactory.getLogger(PlanTracker.class);
    private Plan plan;
    // Please ensure you only change status when you're synchronized on it :)
    private volatile PlanStatus status;
    private OperationDriverFactory driverFactory;
    private ExecutorService service;
    private TaskRegistry registry;
    // There can be only one rollbackThread, so we don't double-rollback
    private AtomicReference<Thread> rollbackThread;
    private Collection<PlanListener> listeners;

    private PlanExecutor planExecutor = null;

    private PlanTracker(Plan plan, PlanStatus status, OperationDriverFactory driverFactory, TaskRegistry registry, Collection<PlanListener> listeners, PlanExecutor planExecutor) {
        this.service = Executors.newCachedThreadPool();
        this.driverFactory = driverFactory;
        this.registry = registry;
        this.plan = plan;
        this.status = status;
        this.rollbackThread = new AtomicReference<>(null);
        this.listeners = listeners;
        this.planExecutor = planExecutor;
    }

    private Collection<Step> getReadySteps() {
        Set<UUID> ready = new HashSet<>();
        for (UUID stepId : status.getPending()) {
            Collection<UUID> prereqs = plan.getPrereqsByStep().getOrDefault(stepId, Collections.EMPTY_LIST);
            if (prereqs.stream().allMatch(prereqId -> status.getCompleted().contains(prereqId))) {
                ready.add(stepId);
            }
        }
        return ready.stream()
                .map(id -> plan.getSteps().get(id))
                .collect(Collectors.toList());
    }

    /**
     * Method to begin the execution of the tracker plan.
     * This method may cause the plan to start from the beginning, or to
     * pick up from its last checkpointed state.
     * Usually, this method will be called by the {@link PlanExecutor};
     * this should only be called directly for unit testing.
     */
    public synchronized void resumeExecution() {
        logger.info("Resuming execution of plan " + plan.getUuid());
        listeners.stream().forEach(l -> l.planStarted(plan, status));
        Collection<StepRunner> initialSteps = new HashSet<>();
        for (UUID id : status.getRunning()) {
            StepRunner runner = new StepRunner(plan.getSteps().get(id));
            runner.setWasRunningPreviously();
            initialSteps.add(runner);
        }
        getReadySteps().stream()
                .map(s -> new StepRunner(s))
                .forEach(r -> initialSteps.add(r));
        for (StepRunner runner : initialSteps) {
            service.submit(runner);
        }
        if (initialSteps.isEmpty()) {
            listeners.stream().forEach(l -> l.planEnded(plan, status, true));
        }
    }

    protected synchronized void startStep(UUID uuid) {
        logger.info("Started step " + uuid);
        synchronized (status) {
            status = status.startStep(uuid);
        }
    }

    protected synchronized void finishStep(UUID uuid) {
        synchronized (status) {
            status = status.finishStep(uuid);
        }
        for (Step step : getReadySteps()) {
            service.submit(new StepRunner(step));
        }
        checkDone();
    }

    protected synchronized void checkDone() {
        if (status.isComplete()) {
            listeners.stream().forEach(l -> l.planEnded(plan, status, true));
        }
    }

    protected synchronized void unravelPlan(Throwable e) {
        final List<Step> toUndo = new ArrayList<>();
        Map<UUID, Step> steps = plan.getSteps();
        for (UUID id : status.getRunning()) {
            toUndo.add(steps.get(id));
        }
        // Add completed steps in reverse order of completion
        for (int i = status.getCompleted().size() - 1; i >= 0; i--) {
            toUndo.add(steps.get(status.getCompleted().get(i)));
        }
        Thread rollback = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Step step : toUndo) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Rollback thread was interrupted");
                        }
                        logger.info("Undoing step " + step.getUuid() + ": " + step.getOperation().getClass());
                        step.getOperation().unravel(registry, driverFactory.makeDriver(step));
                        synchronized (status) {
                            status = status.rolledBackStep(step.getUuid());
                        }
                    }
                    listeners.stream().forEach(l -> l.planEnded(plan, status, false));
                } catch (Throwable e) {
                    crash(e);
                    listeners.stream().forEach(l -> l.planEnded(plan, status, false));
                }
            }
        });
        if (rollbackThread.compareAndSet(null, rollback)) {
            logger.error("Encountered error, rolling back plan", e);
            synchronized (status) {
                status = status.rollback();
            }
            // This sends interrupts to all currently running steps
            service.shutdownNow();
            rollback.start();
        } else if (!(e instanceof InterruptedException)){
            logger.info("Another error encountered, but unravel has already begun", e);
        }
    }

    private synchronized void crash(Throwable e) {
        logger.error("Encountered error during unravel, aborting abruptly", e);
        synchronized (status) {
            status = status.crash();
        }
    }

    public Plan getPlan() {
        return plan;
    }

    public PlanStatus getStatus() {
        return status;
    }

    /**
     * This method causes the given plan to immediately abort.
     * This could potentially leave the system in an indeterminate state.
     * Repeatedly calling this will escalate the means by which the plan is terminated,
     * so it's recommended to only call this once.
     */
    public void abortPlan() {
        if (planExecutor != null) {
            //delete self from the executor
        }
        synchronized (this) {
            // we're already done
            if (status.isComplete()) {
                return;
            }
        }
        Thread rollback = rollbackThread.get();
        if (rollback != null) {
            rollback.interrupt();
        } else {
            unravelPlan(new RuntimeException("Aborting plan due to framework choice"));
        }
    }

    public class StepRunner implements Runnable {
        private Step step;
        private boolean wasRunningPreviously;

        public StepRunner(Step step) {
            this.step = step;
            wasRunningPreviously = false;
        }

        public void setWasRunningPreviously() {
            wasRunningPreviously = true;
        }

        public void run() {
            Operation op = step.getOperation();
            OperationDriver driver = driverFactory.makeDriver(step);
            try {
                if (!wasRunningPreviously) {
                    startStep(step.getUuid());
                }
                listeners.stream().forEach(l -> l.stepBegan(plan, status, step));
                op.doAction(registry, driver);
                finishStep(step.getUuid());
                listeners.stream().forEach(l -> l.stepEnded(plan, status, step));
                checkDone();
            } catch (Throwable e) {
                unravelPlan(e);
                listeners.stream().forEach(l -> l.stepEnded(plan, status, step));
            }
        }
    }

    public static class Builder {
        private Plan plan;
        private ExecutorService service;
        private OperationDriverFactory driverFactory;
        private TaskRegistry registry;
        private Collection<PlanListener> listeners = Collections.EMPTY_LIST;
        private PlanStatus status = null;
        private PlanExecutor planExecutor = null;

        public Builder setPlan(Plan plan) {
            this.plan = plan;
            return this;
        }

        public Builder setDriverFactory(OperationDriverFactory driverFactory) {
            this.driverFactory = driverFactory;
            return this;
        }

        public Builder setRegistry(TaskRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder setListeners(Collection<PlanListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder setStatus(PlanStatus status) {
            this.status = status;
            return this;
        }

        public Builder setPlanExecutor(PlanExecutor planExecutor) {
            this.planExecutor = planExecutor;
            return this;
        }

        public PlanTracker createPlanTracker() {
            if (status == null) {
                status = new PlanStatus(plan);
            }
            return new PlanTracker(plan, status, driverFactory, registry, listeners, planExecutor);
        }
    }
}

