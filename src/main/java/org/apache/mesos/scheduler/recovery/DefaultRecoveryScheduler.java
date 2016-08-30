package org.apache.mesos.scheduler.recovery;

import com.google.protobuf.TextFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.recovery.constrain.LaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.FailureMonitor;
import org.apache.mesos.state.StateStore;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * This class provides the core functionality of the automatic recovery mechanisms.
 */
public class DefaultRecoveryScheduler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final StateStore stateStore;
    private final OfferAccepter offerAccepter;
    private final TaskFailureListener failureListener;
    private final RecoveryRequirementProvider offerReqProvider;
    private final FailureMonitor failureMonitor;
    private final LaunchConstrainer launchConstrainer;
    private final AtomicReference<RecoveryStatus> repairStatusRef;

    public DefaultRecoveryScheduler(
            StateStore stateStore,
            TaskFailureListener failureListener,
            RecoveryRequirementProvider offerReqProvider,
            OfferAccepter offerAccepter,
            LaunchConstrainer launchConstrainer,
            FailureMonitor failureMonitor,
            AtomicReference<RecoveryStatus> repairStatusRef) {
        this.stateStore = stateStore;
        this.offerReqProvider = offerReqProvider;
        this.offerAccepter = offerAccepter;
        this.failureMonitor = failureMonitor;
        this.launchConstrainer = launchConstrainer;
        this.repairStatusRef = repairStatusRef;
        this.failureListener = failureListener;
    }

    /**
     * This function runs the recovery logic. It should be invoked periodically.
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
    public synchronized List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers, Optional<Block> block)
            throws Exception {
        List<OfferID> acceptedOffers = new ArrayList<>();
        updateRecoveryPools(block);

        List<TaskInfo> stopped = repairStatusRef.get().getStopped();
        List<TaskInfo> failed = repairStatusRef.get().getFailed();

        List<RecoveryRequirement> recoveryCandidates = offerReqProvider.getTransientRecoveryOfferRequirements(stopped);
        recoveryCandidates.addAll(offerReqProvider.getPermanentRecoveryOfferRequirements(failed));

        Optional<RecoveryRequirement> recoveryRequirement = Optional.empty();
        if (recoveryCandidates.size() > 0) {
            // Choose a random Task to recover.  No priority is given to transient failures over permanent failures
            // or vice versa.  This avoids a single failure or type of failure which is being resistant to recovery from
            // blocking the recovery of other Tasks which may be able to a make progress.
            // TODO: (gabrielhartmann) Allow for pluggable recovery candidate selection strategies.
            recoveryRequirement = Optional.of(recoveryCandidates.get(new Random().nextInt(recoveryCandidates.size())));
        }

        if (recoveryRequirement.isPresent() && launchConstrainer.canLaunch(recoveryRequirement.get())) {
            log.info("Preparing to launch task");
            OfferEvaluator offerEvaluator = new OfferEvaluator();
            List<OfferRecommendation> recommendations =
                    offerEvaluator.evaluate(recoveryRequirement.get().getOfferRequirement(), offers);
            List<Operation> launchOperations = recommendations.stream()
                    .map(OfferRecommendation::getOperation)
                    .filter(Operation::hasLaunch)
                    .collect(Collectors.toList());

            acceptedOffers = offerAccepter.accept(driver, recommendations);
            if (launchOperations.size() == 1) {
                // We could've launched nothing if the offer didn't fit
                launchConstrainer.launchHappened(launchOperations.get(0), recoveryRequirement.get().getRecoveryType());
            }
        }

        updateRecoveryPools(block);
        return acceptedOffers;
    }

    private Collection<TaskInfo> getTerminatedTasks(Optional<Block> block) {
        List<TaskInfo> filteredTerminatedTasks = new ArrayList<TaskInfo>();

        try {
            if (!block.isPresent()) {
                return stateStore.fetchTerminatedTasks();
            }

            String blockName = block.get().getName();
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

    private void updateRecoveryPools(Optional<Block> block) {
        List<TaskInfo> terminatedTasks = new ArrayList<>(getTerminatedTasks(block));

        List<TaskInfo> failed = new ArrayList<>(terminatedTasks.stream()
                .filter(failureMonitor::hasFailed)
                .collect(Collectors.toList()));
        failed = failed.stream().distinct().collect(Collectors.toList());
        failed.stream().forEach(it -> failureListener.taskFailed(it.getTaskId()));

        List<TaskInfo> stopped = terminatedTasks.stream()
                .filter(it -> !failureMonitor.hasFailed(it))
                .collect(Collectors.toList());

        for (TaskInfo terminatedTask : stateStore.fetchTerminatedTasks()) {
            log.info("Found stopped task: {}", TextFormat.shortDebugString(terminatedTask));
            if (failureMonitor.hasFailed(terminatedTask)) {
                log.info("Marking stopped task as failed: {}", TextFormat.shortDebugString(terminatedTask));
            }
        }

        repairStatusRef.set(new RecoveryStatus(stopped, failed));
    }
}
