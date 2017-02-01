package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.reconciliation.Reconciler;
import org.apache.mesos.Protos;
import java.util.*;


/**
 * Step that implements Reconciliation between the Mesos Master and a
 * framework. The Step will be complete when it receives status for all
 * known tasks and then performs implicit reconciliation.
 */
public class ReconciliationStep extends AbstractStep {

    private final Reconciler reconciler;

    /**
     * Factory method.
     *
     * @param reconciler The reconciler to use for reconciliation
     * @return A new ReconciliationStep
     */
    public static final ReconciliationStep create(Reconciler reconciler) {
        return new ReconciliationStep(reconciler);
    }

    private ReconciliationStep(final Reconciler reconciler) {
        super("Reconciliation", Status.PENDING);
        this.reconciler = reconciler;
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
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
        if (!recommendations.isEmpty()) {
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
    public Status getStatus() {
        if (reconciler.isReconciled()) {
            setStatus(Status.COMPLETE);
        }

        return super.getStatus();
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
}
