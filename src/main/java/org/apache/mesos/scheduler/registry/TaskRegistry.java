package org.apache.mesos.scheduler.registry;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.*;
import org.apache.mesos.protobuf.TaskUtil;
import org.apache.mesos.scheduler.txnplan.PlanTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * This tracks all the tasks and executors running on the cluster.
 *
 * It will be the means of safely disseminating status updates on Tasks.
 * It will be the only component that actually gets offers and may act on them.
 *
 * Because of this, the registry is the clearinghouse for launching, monitoring,
 * and killing tasks as well.
 *
 * The registry needs to store data about tasks & their executors.
 * Tasks will be named; executors will be anonymous. A name will have a single TaskInfo (immutable),
 * a status (can change over time, but only user code can change it to an earlier status), and
 * offerrequirements (used to launch the task, but required to only have a single task).
 *
 * Tasks will start out with null Status--this is a signal they need to be launched.
 * Once a task is launched, it'll be in the STAGING state. Subsequent task updates
 * will be routed to the appropriate task, so that queries will return the latest state.
 * We'll automatically kill tasks that don't have their TaskID listed.
 *
 *
 *
 */
public class TaskRegistry {
    private static final Logger logger = LoggerFactory.getLogger(TaskRegistry.class);
    private final Map<String, Task> tasks;
    private final OfferAccepter accepter;
    private volatile SchedulerDriver driver = null;
    private RegistryStorageDriver storageDriver;
    private AtomicReference<Thread> reconcileThread;
    private AtomicBoolean beenReconciled;
    private TaskRegistryLaunchRecorder recorder;

    public TaskRegistry(RegistryStorageDriver storageDriver) {
        this.storageDriver = storageDriver;
        this.tasks = new HashMap<>();
        this.reconcileThread = new AtomicReference<>();
        this.beenReconciled = new AtomicBoolean(false);
        this.recorder = new TaskRegistryLaunchRecorder();
        this.accepter = new OfferAccepter(recorder);
        //TODO if loadAllTasks returns an empty set, maybe we should throw or something
        for (Task task : storageDriver.loadAllTasks()) {
            tasks.put(task.getName(), task);
        }
        startPeriodicImplicitReconciler();
    }

    private void startPeriodicImplicitReconciler() {
        Thread periodicReconciler = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(6 * 60 * 60 * 1000);
                        if (driver != null) {
                            driver.reconcileTasks(Collections.EMPTY_LIST);
                        }
                    } catch (Exception e) {
                        logger.warn("Implicit periodic reconciler interrupted", e);
                    }
                }
            }
        });
        periodicReconciler.setName("periodic implicit reconciler");
        periodicReconciler.setDaemon(true);
        periodicReconciler.start();
    }

    /**
     * Like {@link TaskRegistry#reconcileAsync}, but blocking.
     * @see TaskRegistry#reconcileAsync()
     */
    public void reconcile() {
        Thread reconciler = reconcileAsync();
        if (reconciler != null) {
            try {
                reconciler.join();
            } catch (InterruptedException e) {
                logger.error("Wait for reconciler interrupted", e);
            }
        }
    }

    /**
     * Attempts to begin a reconciliation process. If it results
     * in starting reconciliation, it kicks off the Thread which is
     * managing the process and returns it
     * If another thread is already reconciling, it returns that thread
     * @return The reconciling thread, or null if it already finished
     */
    public Thread reconcileAsync() {
        Thread reconcileThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean retry = false;
                try {
                    reconcileImpl();
                } catch (Throwable t) {
                    logger.error("Encountered fatal error during reconciliation, relaunching", t);
                    retry = true;
                } finally {
                    TaskRegistry.this.reconcileThread.set(null);
                }
                if (retry) {
                    reconcileAsync();
                }
            }
        });
        reconcileThread.setDaemon(true);
        reconcileThread.setName("Reconciliation thread");
        if (this.reconcileThread.compareAndSet(null, reconcileThread)) {
            reconcileThread.start();
            return reconcileThread;
        } else {
            //someone else will reconcile
            return this.reconcileThread.get();
        }
    }

    private void reconcileImpl() {
        // Directions from the Mesos docs: http://mesos.apache.org/documentation/latest/reconciliation/
        // (Instead of real time, we use the fact that the statuses in of a Task are monotonic)
        // 1. let start = now()
        // 2. let remaining = { T in tasks | T is non-terminal }
        // 3. Perform reconciliation: reconcile(remaining)
        // 4. Wait for status updates to arrive (use truncated exponential backoff). For each update, note the time of arrival.
        // 5. let remaining = { T in remaining | T.last_update_arrival() < start }
        // 6. If remaining is non-empty, go to 3.
        long sleepTime = 1000;
        // this is 2.
        List<Task> remaining = getAllTasks().stream()
                .filter(t -> t.hasStatus() && !TaskUtils.isTerminated(t.getLatestTaskStatus()))
                .collect(Collectors.toList());
        // this is 1. (must happen after 2. so that we can rely on the monotonicity of `remaining`
        Map<String, Integer> prevEpochStatusCounts = new HashMap<>();
        for (Task task : remaining) {
            prevEpochStatusCounts.put(task.getName(), task.getTaskStatuses().size());
        }
        // this is 6. (the code's written for a do/while, but we'll skip reconcilation if unneeded)
        while (!remaining.isEmpty()) {
            if (driver == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // this sleep is just to avoid busy-spinning
                }
                continue;
            }
            // this is 3.
            driver.reconcileTasks(remaining.stream()
                    .map(Task::getLatestTaskStatus)
                    .collect(Collectors.toList()));
            if (sleepTime < 5*60*1000) {
                logger.info("Performing reconciliation, timeout is " + sleepTime);
            } else {
                logger.warn("Performing reconciliation, timeout has grown to over 5 minutes: " + sleepTime);
            }
            try {
                // this is 4.
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                logger.warn("Sleep for reconcile ended early", e);
            }
            // this is 5.
            List<Task> nextRemaining = remaining.stream()
                    .filter(t -> t.getTaskStatuses().size() > prevEpochStatusCounts.get(t.getName()))
                    .collect(Collectors.toList());
            int numReconciled = remaining.size() - nextRemaining.size();
            logger.info("Reconciled " + numReconciled + ", " + nextRemaining.size() + " are left");
            remaining = nextRemaining;
            //TODO this is the step for an exponential dist, but we should use truncated exponential dist
            // We randomize the sleep time by 10% to avoid accidentally overwhelming the master
            sleepTime += sleepTime * (0.9 + Math.random() * 0.2);
        }
        logger.info("Reconciliation finished");
        beenReconciled.set(true);
    }

    /**
     * Call this each time we reregister to reset the reconcilation flag
     */
    public void didReregistration() {
        beenReconciled.set(false);
    }

    /**
     * Creates a new task and adds it to the registry so that it will be launched.
     * @param name The name of the new task
     * @param requirement The descriptor of how to launch the task
     * @return The {@link org.apache.mesos.Protos.TaskID} that it will be launched as
     */
    public synchronized Protos.TaskID createTask(String name, OfferRequirement requirement) {
        if (tasks.containsKey(name)) {
            throw new RuntimeException("Cannot create task; already exists: " + name);
        }
        Task task = Task.createTask(name, requirement);
        tasks.put(name, task);
        storageDriver.storeTask(task);
        return task.getRealizedTaskInfo().getTaskId();
    }

    /**
     * Removes a task from the registry.
     *
     * This will cause it to be forgotten, and eventually killed during reconciliation.
     * You may want {@link TaskRegistry#replaceTask(String, OfferRequirement)} instead.
     *
     * @param name The name of the task to destroy.
     */
    public synchronized void destroyTask(String name) {
        if (!tasks.containsKey(name)) {
            throw new RuntimeException("Cannot destroy task; does not exist: " + name);
        }
        tasks.remove(name);
        storageDriver.deleteTask(name);
    }

    /**
     * Replaces the task with the given name with the new descriptor.
     *
     * It's highly recommended that you ensure the previous task is terminated before replacing it,
     * or else its container may not be freed in a timely manner.
     *
     * @see TaskUtil#isTerminalState(Protos.TaskState)
     *
     * @param name Name of task to replace
     * @param requirement new descriptor to launch
     * @return The {@link org.apache.mesos.Protos.TaskID} that the new task will be launched as
     */
    public synchronized Protos.TaskID replaceTask(String name, OfferRequirement requirement) {
        if (!tasks.containsKey(name)) {
            throw new RuntimeException("Cannot replace task; doesn't exist: " + name);
        }
        if (!TaskUtil.isTerminalState(tasks.get(name).getLatestTaskStatus().getState())) {
            logger.warn("replacing non-terminal task!");
        }
        Task task = Task.createTask(name, requirement);
        tasks.put(name, task);
        storageDriver.storeTask(task);
        return task.getRealizedTaskInfo().getTaskId();
    }

    public synchronized Task getTask(String name) {
        return tasks.get(name);
    }

    public synchronized boolean hasTask(String name) {
        return tasks.containsKey(name);
    }

    public synchronized Collection<Task> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Computes all of the tasks which might share an executor with the given tasks, as of now.
     *
     * The return value will be a superset or equal to the input. If there are no shared
     * executors, it will be equal.
     *
     * This is used by the {@link PlanTracker}.
     *
     * @param taskNames The tasks to find other potential interferences with
     * @return All possible interfering tasks
     */
    public Collection<String> computeImplicitInterferences(Collection<String> taskNames) {
        /*
        Algorithm:
        1. Get all tasks in registry and build interference mappings.
        1.1. Build a lookup from ExecutorInfo (identity for an executor) to Task
        2. For each input task, use its ExecutorInfo to retrieve interfering tasks
         */
        Collection<Task> allTasks = getAllTasks();
        Map<Protos.ExecutorInfo, List<String>> sharedExecutors = new HashMap<>();
        Map<String, Task> index = new HashMap<>();
        for (Task task : allTasks) {
            index.put(task.getName(), task);
            Protos.TaskInfo taskInfo = task.getRealizedTaskInfo();
            if (taskInfo == null) {
                continue;
            }
            Protos.ExecutorInfo info = taskInfo.getExecutor();
            if (info == null) {
                continue;
            }
            if (sharedExecutors.containsKey(info)) {
                sharedExecutors.get(info).add(task.getName());
            } else {
                List<String> list = new ArrayList<>();
                list.add(task.getName());
                sharedExecutors.put(info, list);
            }
        }
        Set<String> output = new HashSet<>();
        for (String name : taskNames) {
            Task task = index.get(name);
            Protos.TaskInfo taskInfo = task.getRealizedTaskInfo();
            if (taskInfo == null) {
                continue;
            }
            Protos.ExecutorInfo info = taskInfo.getExecutor();
            output.addAll(sharedExecutors.get(info));
        }
        return output;
    }

    /**
     * Gets the most recently observed SchedulerDriver.
     *
     * This method isn't synchronized because it only accesses a volatile variable.
     *
     * @return a SchedulerDriver.
     */
    public SchedulerDriver getSchedulerDriver() {
        return driver;
    }

    public synchronized void handleOffers(SchedulerDriver driver, List<Protos.Offer> offers) {
        //logger.info("Got offers: " + offers);
        this.driver = driver;

        if (!beenReconciled.get()) {
            logger.info("Haven't reconciled yet, so we'll decline offers for now");
            offers.stream().map(Protos.Offer::getId).forEach(driver::declineOffer);
            return;
        }

        List<Task> pendingTasks = tasks.values()
                .stream()
                .filter(t -> !t.hasStatus())
                .collect(Collectors.toList());
        logger.info("Attempting to launch " + pendingTasks.size() + " new tasks");
        //TODO all code under this line could run on another thread
        OfferEvaluator evaluator = new OfferEvaluator();
        List<OfferRecommendation> recommendations = new ArrayList<>();
        List<Protos.Offer> uneededOffers = new ArrayList<>();
        for (Protos.Offer offer : offers) {
            Task matched = null;
            for (Task task : pendingTasks) {
                recommendations = evaluator.evaluate(task.getRequirement(), offer);
                if (!recommendations.isEmpty()) {
                    matched = task;
                    break;
                }
            }
            if (matched != null) {
                logger.info("Found a match--launching a task named " + matched.getName());
                // Found a recommendation
                pendingTasks.remove(matched);
                storageDriver.storeTask(matched);
                synchronized (matched) {
                    matched.notifyAll();
                }
                accepter.accept(driver, recommendations);
                matched.launch(recorder.getLatestInfo());
            } else {
                uneededOffers.add(offer);
            }
        }
        Set<String> expectedResourceIDs = new HashSet<>();
        Set<String> expectedVolumeIDs = new HashSet<>();
        for (Task task : getAllTasks()) {
            OfferRequirement req = task.getRequirement();
            expectedResourceIDs.addAll(req.getResourceIds());
            expectedVolumeIDs.addAll(req.getPersistenceIds());
        }
        ResourceCleaner cleaner = new ResourceCleaner(expectedVolumeIDs, expectedResourceIDs);
        int totalRecs = 0;
        for (Protos.Offer offer : uneededOffers) {
            List<OfferRecommendation> cleanerRecs = cleaner.evaluate(Collections.singletonList(offer));
            totalRecs += cleanerRecs.size();
            accepter.accept(driver, cleanerRecs);
        }
        logger.info("Cleaner made " + totalRecs + " recommendations");
        for (Protos.Offer o : uneededOffers) {
            driver.declineOffer(o.getId());
        }
        logger.info("accepted any offer recs; end of handleOffers");
    }

    public synchronized void handleStatusUpdate(SchedulerDriver driver, Protos.TaskStatus status) {
        logger.info("Got a task status: " + status);
        this.driver = driver;
        Protos.TaskID msgId = status.getTaskId();
        Optional<Task> maybeTask = tasks.values().stream()
                .filter(t -> t.getRealizedTaskInfo().getTaskId().equals(msgId))
                .findFirst();
        if (maybeTask.isPresent()) {
            Task task = maybeTask.get();
            //We're actually updating the status in the wrong order
            //It's possible for another thread to get lucky and read the new
            //status before we've persisted it
            //TODO ^^^ this is a bug, but pretty minor, IMO
            task.updateStatus(status);
            storageDriver.storeTask(task);
            synchronized (task) {
                task.notifyAll();
            }
        } else {
            logger.warn("Heard from unregistered task " + msgId + " so we're killing it.");
            driver.killTask(msgId);
        }
    }
}
