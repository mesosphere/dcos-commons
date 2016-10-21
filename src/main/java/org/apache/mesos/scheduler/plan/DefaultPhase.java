package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.ChainedObserver;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default Phase implementation tracks Blocks both by their UUID and by
 * their ordering. It is an immutable class that can be constructed either
 * directly, or using a fluent style builder.
 */
public class DefaultPhase extends ChainedObserver implements Phase {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UUID id = UUID.randomUUID();
    private final String name;
    private final Strategy<Block> strategy;
    private final List<String> errors;
    private final List<Block> blocks;

    public DefaultPhase(String name, List<Block> blocks, Strategy<Block> strategy, List<String> errors) {
        this.name = name;
        this.blocks = blocks;
        this.strategy = strategy;
        this.errors = errors;
    }

    @Override
    public Strategy<Block> getStrategy() {
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
        if (getStrategy().isInterrupted()) {
            return Status.WAITING;
        }

        return PlanUtils.getStatus(getChildren());
    }

    @Override
    public void setStatus(Status status) {
        PlanUtils.setStatus(getChildren(), status);
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
    public List<Block> getChildren() {
        return blocks;
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
