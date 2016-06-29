package org.apache.mesos.scheduler.txnplan;

import org.apache.mesos.scheduler.registry.TaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * This thing runs the Plans for you. It's definitely not serializable, but its serializable state
 * should be encapsulated by the PlanStatus.
 *
 * It's highly likely that PlanListener is buggy!!!
 *
 * TODO needs a "kill()" function to terminate early
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
    private AtomicBoolean beganRollback;
    private Collection<PlanListener> listeners;

    public PlanTracker(Plan plan, ExecutorService service, OperationDriverFactory driverFactory, TaskRegistry registry, Collection<PlanListener> listeners) {
        this(plan, new PlanStatus(plan), service, driverFactory, registry, listeners);
    }

    public PlanTracker(Plan plan, PlanStatus status, ExecutorService service, OperationDriverFactory driverFactory, TaskRegistry registry, Collection<PlanListener> listeners) {
        this.service = service;
        this.driverFactory = driverFactory;
        this.registry = registry;
        this.plan = plan;
        this.status = status;
        this.beganRollback = new AtomicBoolean(false);
        this.listeners = listeners;
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

    protected synchronized void rollback(Throwable e) {
        if (beganRollback.compareAndSet(false, true)) {
            logger.error("Encountered error, rolling back plan", e);
            synchronized (status) {
                status = status.rollback();
            }
            service.shutdownNow();
            final List<Step> toUndo = new ArrayList<>();
            Map<UUID, Step> steps = plan.getSteps();
            for (UUID id : status.getRunning()) {
                toUndo.add(steps.get(id));
            }
            for (int i = status.getCompleted().size() - 1; i >= 0; i--) {
                toUndo.add(steps.get(status.getCompleted().get(i)));
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (Step step : toUndo) {
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
            }).start();
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
                rollback(e);
                listeners.stream().forEach(l -> l.stepEnded(plan, status, step));
            }
        }
    }
}

