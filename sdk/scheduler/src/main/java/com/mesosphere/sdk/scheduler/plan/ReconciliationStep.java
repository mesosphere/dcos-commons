package com.mesosphere.sdk.scheduler.plan;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.DefaultObservable;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.plan.PodInstanceRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Step that implements Reconciliation between the Mesos Master and a
 * framework. The Step will be complete when it receives status for all
 * known tasks and then performs implicit reconciliation.
 */
public class ReconciliationStep extends DefaultObservable implements Step {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationStep.class);

    private final Reconciler reconciler;
    private final UUID id = UUID.randomUUID();
    private Status status;

    /**
     * Factory method.
     * @param reconciler The reconciler to use for reconciliation.
     * @return A new ReconciliationStep
     */
    public static final ReconciliationStep create(Reconciler reconciler) {
        return new ReconciliationStep(reconciler);
    }

    private ReconciliationStep(final Reconciler reconciler) {
        this.reconciler = reconciler;
        setStatus(Status.PENDING);
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        try {
            reconciler.start();
            setStatus(Status.PREPARED);
        } catch (Exception ex) {
            setStatus(Status.PENDING);
            logger.error("Failed to retrieve TaskStatus Set to proceed with reconciliation.", ex);
        }
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        if (!operations.isEmpty()) {
            throw new UnsupportedOperationException(
                    "updateOfferStatus() not expected: getOfferRequirement() always returns null");
        }
    }

    @Override
    public Optional<String> getAsset() {
        return Optional.empty();
    }

    @Override
    public boolean isAssetDirty() {
        return false;
    }

    @Override
    public void restart() {
        // reset state, while silently allowing the reconciler continue any pending work
        setStatus(Status.PENDING);
        reconciler.start();
    }

    @Override
    public void forceComplete() {
        reconciler.forceComplete();
        setStatus(Status.COMPLETE);
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
    public Status getStatus() {
        if (reconciler.isReconciled()) {
            setStatus(Status.COMPLETE);
        }

        return status;
    }

    @Override
    public List<Element<?>> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public Strategy<? extends Step> getStrategy() {
        return new SerialStrategy<>();
    }

    @Override
    public String getMessage() {
        if (isPending()) {
            return "Reconciliation pending";
        } else if (!reconciler.isReconciled()) {
            return "Reconciliation in progress unreconciled tasks = " + reconciler.remaining();
        } else {
            return "Reconciliation complete";
        }
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    /**
     * Updates the status setting and logs the outcome. Should only be called either by tests, by
     * {@code this}, or by subclasses.
     *
     * @param newStatus the new status to be set
     */
    @VisibleForTesting
    void setStatus(Status newStatus) {
        Status oldStatus = status;
        status = newStatus;
        logger.info(getName() + ": changed status from: " + oldStatus + " to: " + newStatus);

        if (!Objects.equals(oldStatus, newStatus)) {
            notifyObservers();
        }
    }
}
