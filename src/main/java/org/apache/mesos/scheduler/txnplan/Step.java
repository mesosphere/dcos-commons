package org.apache.mesos.scheduler.txnplan;

import java.util.UUID;

/**
 * Created by dgrnbrg on 6/20/16.
 */
public class Step {
    private Operation operation;
    private UUID uuid;
    private Plan plan;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Step step = (Step) o;

        if (!operation.equals(step.operation)) return false;
        if (!uuid.equals(step.uuid)) return false;
        return plan.equals(step.plan);

    }

    @Override
    public int hashCode() {
        int result = operation.hashCode();
        result = 31 * result + uuid.hashCode();
        result = 31 * result + plan.hashCode();
        return result;
    }

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
