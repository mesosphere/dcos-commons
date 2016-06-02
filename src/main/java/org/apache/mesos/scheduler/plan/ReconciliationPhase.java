package org.apache.mesos.scheduler.plan;

import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.reconciliation.TaskStatusProvider;

import java.util.Arrays;
import java.util.UUID;

/**
 * Phase that implements reconciliation. It has exactly one Block, the
 * reconciliation block.
 */
public final class ReconciliationPhase extends DefaultPhase {

    public static ReconciliationPhase create(Reconciler reconciler, TaskStatusProvider taskProvider) {
        return new ReconciliationPhase(reconciler, taskProvider);
    }

    public ReconciliationPhase(Reconciler reconciler, TaskStatusProvider taskProvider) {
        super(UUID.randomUUID(),
                "Reconciliation",
                Arrays.asList(ReconciliationBlock.create(reconciler, taskProvider)));
    }


}
