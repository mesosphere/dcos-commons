package org.apache.mesos.scheduler.txnplan;

import java.util.UUID;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public class Step {
    private Operation operation;
    private UUID uuid;
    private Plan plan;

    private Step() {
    }

    public Step(Operation operation, Plan plan) {
        this.operation = operation;
        this.uuid = UUID.randomUUID();
        this.plan = plan;
    }

    public void requires(Step other) {
        plan.addDependency(other.getUuid(), this.uuid);
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public Operation getOperation() {
        return this.operation;
    }
}
