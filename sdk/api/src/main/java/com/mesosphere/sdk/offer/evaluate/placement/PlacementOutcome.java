package com.mesosphere.sdk.offer.evaluate.placement;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The outcome of invoking a {@link PlacementRule}. Describes whether the evaluation passed or failed, and the
 * reason(s) why. Supports a nested tree of outcomes which describe any sub-evaluations which may have been performed
 * within the {@link PlacementRule}.
 */
public class PlacementOutcome {

    /**
     * The outcome value.
     */
    private enum Type {
        PASS,
        FAIL
    }

    private final Type type;
    private final String source;
    private final Collection<PlacementOutcome> children;
    private final String reason;

    /**
     * Returns a new passing outcome object with the provided descriptive reason.
     *
     * @param source the object which produced this outcome, whose class name will be labeled as the origin
     * @param reasonFormat {@link String#format(String, Object...)} compatible format string describing the pass reason
     * @param reasonArgs format arguments, if any, to apply against {@code reasonFormat}
     */
    public static PlacementOutcome.Builder pass(
            Object source,
            String reasonFormat,
            Object... reasonArgs) {
        return new PlacementOutcome.Builder(
                Type.PASS,
                source,
                reasonFormat,
                reasonArgs);
    }

    /**
     * Returns a new failing outcome object with the provided descriptive reason.
     *
     * @param source the object which produced this outcome, whose class name will be labeled as the origin
     * @param reasonFormat {@link String#format(String, Object...)} compatible format string describing the fail reason
     * @param reasonArgs format arguments, if any, to apply against {@code reasonFormat}
     */
    public static PlacementOutcome.Builder fail(
            Object source,
            String reasonFormat,
            Object... reasonArgs) {
        return new PlacementOutcome.Builder(
                Type.FAIL,
                source,
                reasonFormat,
                reasonArgs);
    }

    private PlacementOutcome(
            Type type,
            Object source,
            Collection<PlacementOutcome> children,
            String reason) {
        this.type = type;
        this.source = source.getClass().getSimpleName();
        this.children = children;
        this.reason = reason;
    }

    /**
     * Returns whether this outcome was passing ({@code true}) or failing ({@code false}).
     */
    public boolean isPassing() {
        return type == Type.PASS;
    }

    /**
     * Returns the name of the object which produced this response.
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the reason that this response is passing or failing.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns any nested outcomes which resulted in this decision.
     */
    public Collection<PlacementOutcome> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return String.format("%s(%s): %s", isPassing() ? "PASS" : "FAIL", getSource(), getReason());
    }

    /**
     * Builder for constructing {@link PlacementOutcome} instances.
     */
    public static class Builder {
        private final Type type;
        private final Object source;
        private final Collection<PlacementOutcome> children;
        private final String reason;

        public Builder(
                Type type,
                Object source,
                String reasonFormat,
                Object... reasonArgs) {
            this.type = type;
            this.source = source;
            this.children = new ArrayList<>();
            this.reason = String.format(reasonFormat, reasonArgs);
        }

        public Builder addChild(PlacementOutcome child) {
            children.add(child);
            return this;
        }

        public Builder addAllChildren(Collection<PlacementOutcome> children) {
            this.children.addAll(children);
            return this;
        }

        public PlacementOutcome build() {
            return new PlacementOutcome(type, source, children, reason);
        }
    }
}
