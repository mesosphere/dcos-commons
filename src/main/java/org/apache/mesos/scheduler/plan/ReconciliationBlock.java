package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.reconciliation.Reconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;


/**
 * Block that implements Reconciliation between the Mesos Master and a
 * framework. The Block will be complete when it receives status for all
 * known tasks and then performs implicit reconciliation.
 */
public class ReconciliationBlock implements Block {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationBlock.class);

    private final Reconciler reconciler;
    private final UUID id = UUID.randomUUID();
    private boolean isPending = true; // reconciler hasn't start()ed yet

    /**
     * Factory method.
     * @param reconciler The reconciler to use for reconciliation.
     * @return A new ReconciliationBlock
     */
    public static final ReconciliationBlock create(Reconciler reconciler) {
        return new ReconciliationBlock(reconciler);
    }

    private ReconciliationBlock(final Reconciler reconciler) {
        this.reconciler = reconciler;
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
    public Optional<OfferRequirement> start() {
        try {
            reconciler.start();
            isPending = false;
        } catch (Exception ex) {
            isPending = true; // try again later
            logger.error("Failed to retrieve TaskStatus Set to proceed with reconciliation.", ex);
        }
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        if (!operations.isEmpty()) {
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
}
