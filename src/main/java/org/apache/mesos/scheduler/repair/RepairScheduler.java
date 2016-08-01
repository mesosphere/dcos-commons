package org.apache.mesos.scheduler.repair;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.repair.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.repair.monitor.FailureMonitor;
import org.apache.mesos.state.StateStore;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * This class provides the core functionality of the automatic repair mechanisms.
 */
public class RepairScheduler {
    private final Log log = LogFactory.getLog(getClass());

    private final String targetConfigName;
    private final StateStore stateStore;
    private final OfferAccepter offerAccepter;
    private final TaskFailureListener failureListener;
    private final RepairOfferRequirementProvider offerReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;
    private final AtomicReference<RepairStatus> repairStatusRef;

    public RepairScheduler(
            String targetConfigName,
            StateStore stateStore,
            TaskFailureListener failureListener,
            RepairOfferRequirementProvider offerReqProvider,
            OfferAccepter offerAccepter,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            AtomicReference<RepairStatus> repairStatusRef) {
        this.targetConfigName = targetConfigName;
        this.stateStore = stateStore;
        this.offerReqProvider = offerReqProvider;
        this.offerAccepter = offerAccepter;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        this.repairStatusRef = repairStatusRef;
        this.failureListener = failureListener;
    }

    /**
     * This function runs the repair logic. It should be invoked periodically.
     *
     * This function is synchronized in order to avoid launchConstrainer race conditions.
     * The situation we need to avoid is 2 threads getting approval from the launchConstrainer, when only one should
     * have been allowed to proceed. If this issue was dealt with via another mechanism, we could make this function
     * unsynchronized.
     *
     * @param driver The current SchedulerDriver
     * @param offers A list of offers to use to launch tasks
     * @param block The current block, or null if there isn't any
     * @return IDs of accepted offers
     * @throws Exception
     */
    public synchronized List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers, Block block)
            throws Exception {
        List<OfferID> acceptedOffers = new ArrayList<>();
        List<TaskInfo> terminatedTasks = new ArrayList<>(getTerminatedTasks(block));

        // Compute the stopped and failed pool for UI
        RepairStatus oldStatus = repairStatusRef.get();
        if (oldStatus == null) {
            oldStatus = new RepairStatus(Collections.emptyList(), Collections.emptyList());
        }
        List<String> oldFailed = oldStatus.getFailed();
        List<String> newFailed = new ArrayList<>(terminatedTasks.stream()
                .filter(failureMonitor::hasFailed)
                .map(TaskInfo::getName)
                .collect(Collectors.toList()));
        newFailed.addAll(oldFailed);
        //TODO (ghartman) possibly write this list down in ZK, to ensure the UI doesn't change upon restart
        newFailed = newFailed.stream().distinct().collect(Collectors.toList());
        List<String> newStopped = terminatedTasks.stream()
                .filter(it -> !failureMonitor.hasFailed(it))
                .map(TaskInfo::getName)
                .collect(Collectors.toList());

        Optional<OfferRequirement> offerReq = Optional.empty();
        boolean replacingFailed = false;

        if (terminatedTasks.size() > 0) {
            TaskInfo terminatedTask = terminatedTasks.get(new Random().nextInt(terminatedTasks.size()));
            log.info("Found stopped task");
            if (failureMonitor.hasFailed(terminatedTask)) {
                log.info("Marking stopped task as failed");
                failureListener.taskFailed(terminatedTask.getTaskId());
            } else {
                log.info("Relaunching stopped task in place");
                offerReq = Optional.of(offerReqProvider.getReplacementOfferRequirement(terminatedTask));
            }
        } else {
            log.info("Launching a failed task");
            replacingFailed = true;
            offerReq = offerReqProvider.maybeGetNewOfferRequirement(targetConfigName, block);
        }

        if (offerReq.isPresent() && launchConstrainer.canLaunch(offerReq.get())) {
            log.info("Preparing to launch task");
            OfferEvaluator offerEvaluator = new OfferEvaluator();
            List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offerReq.get(), offers);
            if (!recommendations.isEmpty()) {
                // we're replacing some task(s)
                Set<String> relaunchedTasks = recommendations.stream()
                        .filter(it -> it.getOperation().hasLaunch())
                        .flatMap(it -> it.getOperation().getLaunch().getTaskInfosList().stream())
                        .map(TaskInfo::getName)
                        .collect(Collectors.toSet());
                if (replacingFailed) {
                    // we'll filter their names out of the failed pool
                    newFailed = newFailed.stream()
                            .filter(it -> !relaunchedTasks.contains(it))
                            .collect(Collectors.toList());
                } else {
                    // we'll filter their names out of the stopped pool
                    newStopped = newStopped.stream()
                            .filter(it -> !relaunchedTasks.contains(it))
                            .collect(Collectors.toList());
                }
            }
            List<Operation> launchOperations = recommendations.stream()
                    .map(OfferRecommendation::getOperation)
                    .filter(Operation::hasLaunch)
                    .collect(Collectors.toList());
            if (launchOperations.size() > 1) {
                throw new IllegalArgumentException("Repairs should only launch one task at a time, found "
                        + launchOperations.size() + " tasks");
            }
            acceptedOffers = offerAccepter.accept(driver, recommendations);
            if (launchOperations.size() == 1) {
                // We could've launched nothing if the offer didn't fit
                launchConstrainer.launchHappened(launchOperations.get(0));
            }
        }

        repairStatusRef.set(new RepairStatus(newStopped, newFailed));

        return acceptedOffers;
    }

    private Collection<TaskInfo> getTerminatedTasks(Block block) {
        List<TaskInfo> filteredTerminatedTasks = new ArrayList<TaskInfo>();

        try {
            if (block == null) {
                return stateStore.fetchTerminatedTasks();
            }

            String blockName = block.getName();
            for (TaskInfo taskInfo : stateStore.fetchTerminatedTasks()) {
                if (!taskInfo.getName().equals(blockName)) {
                    filteredTerminatedTasks.add(taskInfo);
                }
            }
        } catch (Exception ex) {
            log.error("Stopped to fetch terminated tasks.");
        }

        return filteredTerminatedTasks;
    }

}
