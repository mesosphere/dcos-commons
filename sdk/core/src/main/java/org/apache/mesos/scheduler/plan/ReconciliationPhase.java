package org.apache.mesos.scheduler.plan;

import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;

import java.util.Arrays;
import java.util.Collections;

/**
 * Phase that implements reconciliation. It has exactly one Block, the
 * reconciliation block.
 */
public final class ReconciliationPhase extends DefaultPhase {

    public static ReconciliationPhase create(Reconciler reconciler) {
        return new ReconciliationPhase(reconciler);
    }

    private ReconciliationPhase(Reconciler reconciler) {
        super("Reconciliation",
                Arrays.asList(ReconciliationBlock.create(reconciler)),
                new SerialStrategy<>(),
                Collections.emptyList());
    }
}
