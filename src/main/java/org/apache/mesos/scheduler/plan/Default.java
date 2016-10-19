package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.ChainedObserver;
import org.apache.mesos.scheduler.plan.strategy.Strategy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An ordered list of {@link Phase}s, composed into a {@link Plan}. It may
 * optionally contain a List of errors associated with the phase.
 */
public class Default extends ChainedObserver implements Plan {

    private final UUID id = UUID.randomUUID();
    private final Strategy<Phase> strategy;
    private final List<Element> phases;
    private final List<String> errors;
    private final String name;
    private Status status;

    public Default(
            final String name,
            final Strategy<Phase> strategy,
            final List<Element> phases,
            final List<String> errors) {

        this.name = name;
        this.strategy = strategy;
        this.phases = phases;
        this.errors = errors;

        // Initialize to non-null
        this.status = Status.PENDING;
        // then initialize to aggregate status of children
        this.status = getStatus();

        getChildren().forEach(phase -> phase.subscribe(this));
    }

    @Override
    public List<Element> getChildren() {
        return phases;
    }

    @Override
    public Strategy<Phase> getStrategy() {
        return strategy;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Status getStatus() {
        status = PlanUtils.getStatus(getChildren());
        return status;
    }

    @Override
    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public void update(Protos.TaskStatus status) {
        PlanUtils.update(status, getChildren());
    }

    @Override
    public void restart() {
        PlanUtils.restart(getChildren());
    }

    @Override
    public void forceComplete() {
        PlanUtils.forceComplete(getChildren());
    }

    @Override
    public String getMessage() {
        return PlanUtils.getMessage(this);
    }

    @Override
    public List<String> getErrors() {
        return PlanUtils.getErrors(errors, getChildren());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
