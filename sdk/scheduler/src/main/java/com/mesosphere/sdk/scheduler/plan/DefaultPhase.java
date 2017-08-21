package com.mesosphere.sdk.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The Default Phase implementation tracks {@link Step}s both by their UUID and by their ordering. It is an immutable
 * class.
 *
 */
public class DefaultPhase implements Phase {

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
    public List<String> getErrors() {
        return getErrors(errors);
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
