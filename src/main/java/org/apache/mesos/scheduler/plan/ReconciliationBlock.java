package org.apache.mesos.scheduler.plan;

import java.util.UUID;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.reconciliation.TaskStatusProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Block that implements Reconciliation between the Mesos Master and a
 * framework. The Block will be complete when it receives status for all
 * known tasks and then performs implicit reconciliation.
 */
public class ReconciliationBlock implements Block {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationBlock.class);

    private final Reconciler reconciler;
    private final TaskStatusProvider taskProvider;
    private final UUID id = UUID.randomUUID();
    private boolean isPending = true; // reconciler hasn't start()ed yet

    /**
     * Factory method.
     * @param reconciler The reconciler to use for reconciliation.
     * @return A new ReconciliationBlock
     */
    public static final ReconciliationBlock create(
            Reconciler reconciler, TaskStatusProvider taskProvider) {
        return new ReconciliationBlock(reconciler, taskProvider);
    }

    private ReconciliationBlock(
            final Reconciler reconciler, final TaskStatusProvider taskProvider) {
        this.reconciler = reconciler;
        this.taskProvider = taskProvider;
    }

    @Override
    public boolean isPending() {
        return isPending;
    }

    @Override
    public boolean isInProgress() {
        if (isPending()) {
            return false;
        }
        return !reconciler.isReconciled();
    }

    @Override
    public boolean isComplete() {
        if (isPending()) {
            return false;
        }
        return reconciler.isReconciled();
    }

    @Override
    public OfferRequirement start() {
        try {
            reconciler.start(taskProvider.getTaskStatuses());
            isPending = false;
        } catch (Exception ex) {
            isPending = true; // try again later
            logger.error("Failed to retrieve TaskStatus Set to proceed with reconciliation.", ex);
        }
        return null;
    }

    @Override
    public void updateOfferStatus(boolean accepted) {
        if (accepted) {
            throw new UnsupportedOperationException(
                    "updateOfferStatus() not expected: start() always returns null");
        }
    }

    @Override
    public void restart() {
        // reset state, while silently allowing the reconciler continue any pending work
        isPending = true;
    }

    @Override
    public void forceComplete() {
        isPending = false;
        reconciler.forceComplete();
    }

    @Override
    public void update(Protos.TaskStatus status) {
        reconciler.update(status);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return "Reconciliation";
    }

    @Override
    public String getMessage() {
        if (isPending) {
            return "Reconciliation pending";
        } else if (!reconciler.isReconciled()) {
            return "Reconciliation in progress unreconciled tasks = " + reconciler.remaining();
        } else {
            return "Reconciliation complete";
        }
    }

    /**
     * @return The reconciler used for reconciliation.
     */
    public Reconciler getReconciler() {
        return reconciler;
    }
}
