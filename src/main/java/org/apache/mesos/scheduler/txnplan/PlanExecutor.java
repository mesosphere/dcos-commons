package org.apache.mesos.scheduler.txnplan;

import org.apache.mesos.scheduler.registry.TaskRegistry;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This implements the logic of storing the plan's UUID on submission,
 * and then choosing the plans to launch as needed.
 *
 * It'll only need to serialize the UUID queuing structure; the rest is DepInj
 *
 * Created by dgrnbrg on 6/23/16.
 */
public class PlanExecutor {
    // Access to planQueue and planIndex should be synchronized on "this"
    private HashMap<String, Queue<UUID>> planQueue;
    private HashMap<UUID, PlanTracker> planIndex;
    private Set<UUID> runningPlans;

    private ExecutorService executorService;
    private TaskRegistry registry;
    private OperationDriverFactory driverFactory;
    private PlanListener internalListener;
    private PlanStorageDriver storageDriver;

    public PlanExecutor(TaskRegistry registry, OperationDriverFactory driverFactory, PlanStorageDriver storageDriver) {
        this.planQueue = new HashMap<>();
        this.planIndex = new HashMap<>();
        this.runningPlans = new HashSet<>();
        this.executorService = Executors.newCachedThreadPool();
        this.driverFactory = driverFactory;
        this.storageDriver = storageDriver;
        this.registry = registry;
        this.internalListener = new PlanListener() {
            @Override
            public void stepBegan(Plan plan, PlanStatus status, Step step) {
                storageDriver.saveStatusForPlan(status);
            }

            @Override
            public void stepEnded(Plan plan, PlanStatus status, Step step) {
                storageDriver.saveStatusForPlan(status);
            }

            @Override
            public void planStarted(Plan plan, PlanStatus status) {

            }

            @Override
            public void planEnded(Plan plan, PlanStatus status, boolean succeeded) {
                storageDriver.saveStatusForPlan(status);
                doScheduling();
            }
        };
        //TODO load data from storageDriver
        //First, load the scheduler state
        //Then, load the plans
        //Then, add any plans not being scheduled currently
        //lastly, resume any plans that should be picked up
    }

    public void submitPlan(Plan plan, Collection<PlanListener> listeners){
        plan.freeze();
        storageDriver.savePlan(plan);
        synchronized (this) {
            UUID id = plan.getUuid();
            List<PlanListener> allListeners = new ArrayList<>(listeners.size() + 1);
            allListeners.addAll(listeners);
            allListeners.add(internalListener);
            PlanTracker tracker = new PlanTracker(plan, executorService, driverFactory, registry, allListeners);
            planIndex.put(id, tracker);
            for (String name : plan.getAffectedTaskNames()) {
                if (!planQueue.containsKey(name)) {
                    planQueue.put(name, new ArrayDeque<>());
                }
                planQueue.get(name).add(id);
            }
        }
        doScheduling();
    }

    private synchronized Collection<PlanTracker> getReadyPlans() {
        //First, we remove completed plans from the queue
        //TODO remove plans from planIndex too
        Set<UUID> finishedPlans = new HashSet<>();
        for (String name : planQueue.keySet()) {
            Queue<UUID> q = planQueue.get(name);
            while (!q.isEmpty() && planIndex.get(q.peek()).getStatus().isComplete()) {
                finishedPlans.add(q.peek());
                q.remove();
            }
        }
        for (UUID id : finishedPlans) {
            planIndex.remove(id);
            runningPlans.remove(id);
        }
        Set<PlanTracker> readyPlans = new HashSet<>();
        //Then, we look for plans
        for (String name : planQueue.keySet()) {
            Queue<UUID> q = planQueue.get(name);
            if (q.isEmpty()) {
                //There are no potentially ready plans here
                continue;
            }
            PlanTracker target = planIndex.get(q.peek());
            //A plan isn't a candidate for being ready if it's already running
            if (runningPlans.contains(target.getPlan().getUuid())) {
                continue;
            }
            Collection<String> interferenceSet = target.getPlan().getAffectedTaskNames();
            //TODO don't comment this out, lol
//            interferenceSet = registry.computeImplicitInterferences(interferenceSet);
            if (interferenceSet.stream()
                    .map(task -> planQueue.get(task).peek())
                    .allMatch(id -> target.getPlan().getUuid().equals(id))) {
                readyPlans.add(target);
            }
        }
        return readyPlans;
    }

    private synchronized void doScheduling() {
        Collection<PlanTracker> trackers = getReadyPlans();
        for (PlanTracker tracker : trackers) {
            runningPlans.add(tracker.getPlan().getUuid());
        }
        storageDriver.saveSchedulerState(planQueue, runningPlans);
        for (PlanTracker tracker : trackers) {
            tracker.resumeExecution();
        }
    }

}
