package org.apache.mesos.scheduler.txnplan;

import org.apache.mesos.scheduler.registry.TaskRegistry;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
    private Map<String, Queue<UUID>> planQueue;
    private Map<UUID, PlanTracker> planIndex;
    private Set<UUID> runningPlans;

    private TaskRegistry registry;
    private OperationDriverFactory driverFactory;
    private PlanListener internalListener;
    private PlanStorageDriver storageDriver;
    // This is only true until this object is interacted with via submission or reload
    // This ensures an erroneous call to reload cannot accidentally clobber the state
    private AtomicBoolean fresh;

    public PlanExecutor(TaskRegistry registry, OperationDriverFactory driverFactory, PlanStorageDriver storageDriverArg) {
        this.planQueue = new HashMap<>();
        this.planIndex = new HashMap<>();
        this.runningPlans = new HashSet<>();
        this.driverFactory = driverFactory;
        // the argument is named differently so that the PlanListener belowe uses the member variable
        this.storageDriver = storageDriverArg;
        this.registry = registry;
        this.fresh = new AtomicBoolean(true);
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
    }

    /**
     * This function reloads the executor's state from storage.
     * As a side-effect, it garbage-collects olds plans as well.
     */
    public synchronized void reloadFromStorage() {
        //First, load the scheduler state
        //Then, load the plans
        //Now, we find any unreferenced plans and delete them from storage & in-memory
        //Then, add any plans not being scheduled currently
        //lastly, resume any plans that should be picked up
        if (!fresh.compareAndSet(true, false)) {
            throw new RuntimeException("Cannot reload state from storage after you do stuff");
        }
        SchedulerState state = storageDriver.loadSchedulerState();
        this.planQueue = state.getPlanQueue();
        this.runningPlans = state.getRunningPlans();
        Map<UUID, Plan> plans = storageDriver.loadPlans();
        Map<UUID, PlanStatus> statuses = new HashMap<>();
        for (UUID id : state.getRunningPlans()) {
            PlanStatus status = storageDriver.tryLoadPlanStatus(id);
            if (status != null) {
                statuses.put(id, status);
            }
        }
        Set<UUID> plansInQueue = new HashSet<>();
        state.getPlanQueue().values().stream()
                .flatMap(q -> q.stream())
                .forEach(id -> plansInQueue.add(id));
        List<UUID> unreferencedPlanIds = plans.keySet().stream()
                .filter(id -> !plansInQueue.contains(id))
                .collect(Collectors.toList());
        unreferencedPlanIds.stream().forEach(plans::remove);
        unreferencedPlanIds.stream().forEach(storageDriver::deletePlan);
        Collection<PlanListener> defaultListeners = Arrays.asList(internalListener);
        //By this loop, unreferenced plans should be GCed, or else we could double-execute by mistake
        for (Plan plan : plans.values()) {
            UUID id = plan.getUuid();
            PlanTracker.Builder builder = new PlanTracker.Builder();
            builder.setPlan(plan)
                    .setDriverFactory(driverFactory)
                    .setRegistry(registry)
                    .setPlanExecutor(this)
                    .setListeners(defaultListeners);
            if (statuses.containsKey(id)) {
                builder.setStatus(statuses.get(id));
            }
            planIndex.put(id, builder.createPlanTracker());
            if (!plansInQueue.contains(id)) {
                addPlanToQueue(plan);
            }
        }
        for (UUID id : runningPlans) {
            planIndex.get(id).resumeExecution();
        }
        doScheduling();
    }

    private void addPlanToQueue(Plan plan) {
        for (String name : plan.getAffectedTaskNames()) {
            if (!planQueue.containsKey(name)) {
                planQueue.put(name, new ArrayDeque<>());
            }
            planQueue.get(name).add(plan.getUuid());
        }
    }

    public void submitPlan(Plan plan) {
        submitPlan(plan, Collections.EMPTY_LIST);
    }

    public void submitPlan(Plan plan, Collection<PlanListener> listeners){
        if (plan.getAffectedTaskNames().isEmpty()) {
            throw new RuntimeException("Every plan must interact with something. Try using a random dummy interfence if you need.");
        }
        fresh.lazySet(false);
        plan.freeze();
        storageDriver.savePlan(plan);
        synchronized (this) {
            UUID id = plan.getUuid();
            List<PlanListener> allListeners = new ArrayList<>(listeners.size() + 1);
            allListeners.addAll(listeners);
            allListeners.add(internalListener);
            PlanTracker tracker = new PlanTracker.Builder()
                    .setPlan(plan)
                    .setDriverFactory(driverFactory)
                    .setRegistry(registry)
                    .setListeners(allListeners)
                    .setPlanExecutor(this)
                    .createPlanTracker();
            planIndex.put(id, tracker);
            addPlanToQueue(plan);
        }
        // doScheduling saves the new state as a side effect
        doScheduling();
    }

    /**
     * Internal method used by {@link PlanTracker} to remove itself from the plane executor
     * @param planId the plan to remove
     */
    protected synchronized void removePlan(UUID planId) {
        planIndex.remove(planId);
        planQueue.values().stream().forEach(q -> q.remove(planId));
        storageDriver.saveSchedulerState(planQueue, runningPlans);
    }

    /**
     * Enumerates all the pending and running plans
     * @return Collection of all not yet completed plans in the tracker
     */
    public Collection<PlanTracker> getAllPlans() {
        List<PlanTracker> trackers = new ArrayList<>(planIndex.size());
        trackers.addAll(planIndex.values());
        return trackers;
    }

    private synchronized Collection<PlanTracker> getReadyPlans() {
        //First, we remove completed plans from the queue
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
