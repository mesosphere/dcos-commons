package com.mesosphere.sdk.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;

import com.google.common.base.Joiner;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An ordered list of {@link Phase}s, composed into a {@link Plan}. It may
 * optionally contain a List of errors associated with the phase.
 *
 */
public class DefaultPlan implements Plan {

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
    public List<String> getErrors() {
        return getErrors(errors);
    }

    @Override
    public String toString() {
        // Provide a nicely formatted tree -- mainly for developer use in e.g. unit tests
        List<String> rows = new ArrayList<>();
        rows.add(String.format("Plan: %s (%s)", getName(), getStatus()));
        for (Phase phase : getChildren()) {
            rows.add(String.format("  Phase: %s (%s)", phase.getName(), phase.getStatus()));
            for (Step step : phase.getChildren()) {
                rows.add(String.format("    Step: %s (%s)", step.getName(), step.getStatus()));
            }
        }
        List<String> errors = getErrors();
        if (!errors.isEmpty()) {
            rows.add("Errors:");
            for (String error : errors) {
                rows.add(String.format("  %s", error));
            }
        }
        return Joiner.on('\n').join(rows);
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
