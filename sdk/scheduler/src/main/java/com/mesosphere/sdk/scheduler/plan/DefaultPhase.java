package com.mesosphere.sdk.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.scheduler.ChainedObserver;
import com.mesosphere.sdk.scheduler.Observable;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The Default Phase implementation tracks {@link Step}s both by their UUID and by their ordering. It is an immutable
 * class.
 *
 * A {@link DefaultPhase} is an {@link Observable} and will forward updates from its steps.
 */
public class DefaultPhase extends ChainedObserver implements Phase {

    private final UUID id = UUID.randomUUID();
    private final String name;
    private final Strategy<Step> strategy;
    private final List<String> errors;
    private final List<Step> steps;

    public DefaultPhase(String name, List<Step> steps, Strategy<Step> strategy, List<String> errors) {
        this.name = name;
        this.steps = steps;
        this.strategy = strategy;
        this.errors = errors;

        getChildren().forEach(step -> step.subscribe(this));
    }

    @Override
    public Strategy<Step> getStrategy() {
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
    public List<Step> getChildren() {
        return steps;
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
