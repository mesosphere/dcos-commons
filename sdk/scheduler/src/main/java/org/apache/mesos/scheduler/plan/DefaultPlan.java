package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.ChainedObserver;
import org.apache.mesos.scheduler.Observable;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An ordered list of {@link Phase}s, composed into a {@link Plan}. It may
 * optionally contain a List of errors associated with the phase.
 *
 * A {@link DefaultPlan} is an {@link Observable} and will forward updates from its {@link Phase}s.
 */
public class DefaultPlan extends ChainedObserver implements Plan {

    private final UUID id = UUID.randomUUID();
    private final Strategy<Phase> strategy;
    private final List<Phase> phases;
    private final List<String> errors;
    private final String name;

    public DefaultPlan(
            final String name,
            final List<Phase> phases,
            final Strategy<Phase> strategy,
            final List<String> errors) {
        this.name = name;
        this.strategy = strategy;
        this.phases = phases;
        this.errors = errors;

        getChildren().forEach(phase -> phase.subscribe(this));
    }

    public DefaultPlan(String name, List<Phase> phases) {
        this(name, phases, new SerialStrategy<>());
    }

    public DefaultPlan(String name, List<Phase> phases, Strategy<Phase> strategy) {
        this(name, phases, strategy, Collections.emptyList());
    }

    @Override
    public List<Phase> getChildren() {
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
        return PlanUtils.getStatus(this);
    }

    @Override
    public void update(Protos.TaskStatus status) {
        PlanUtils.update(this, status);
    }

    @Override
    public void restart() {
        PlanUtils.restart(this);
    }

    @Override
    public void forceComplete() {
        PlanUtils.forceComplete(this);
    }

    @Override
    public String getMessage() {
        return PlanUtils.getMessage(this);
    }

    @Override
    public List<String> getErrors() {
        return PlanUtils.getErrors(errors, this);
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
