package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.scheduler.ChainedObserver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An ordered list of {@link Phase}s, composed into a {@link Plan}. It may
 * optionally contain a List of errors associated with the phase.
 *
 * A {@DefaultPlan} is an {@Observable} and will forward updates from its {@Phase}s.
 */
public class DefaultPlan extends ChainedObserver implements Plan {

    private final List<? extends Phase> phases;
    private final List<String> errors;

    /**
     * Convenience function for constructing a {@link Plan} from a variable
     * list of arguments.
     * @return A new Plan constructed from the argument phases.
     */
    public static DefaultPlan fromArgs(Phase... phases) {
        return fromList(Arrays.asList(phases));
    }

    /**
     * @return {@link Plan} composed of the provided list of {@link Phase}s.
     */
    public static DefaultPlan fromList(List<? extends Phase> phases) {
        return new DefaultPlan(phases, Collections.<String>emptyList());
    }

    /**
     * @param phases The {@link Phase}s for the plan.
     * @param errors The errors associated with the plan.
     * @return A new Plan containing phases with the associated errors.
     */
    public static DefaultPlan withErrors(List<? extends  Phase> phases,
                                         List<String> errors) {
        return new DefaultPlan(phases, errors);
    }

    /**
     * Call above helper methods.
     */
    private DefaultPlan(final List<? extends Phase> phases,
                        final List<String> errors) {
        this.phases = phases;
        this.errors = errors;

        for (Phase phase : phases) {
            phase.subscribe(this);
        }
    }

    /**
     * @return The contained list of {@link Phase}s.
     */
    @Override
    public List<? extends Phase> getPhases() {
        return phases;
    }

    @Override
    public List<String> getErrors() {
        return errors;
    }

    /**
     * @return True if all {@link Block}s in all {@link Phase}s are complete.
     */
    @Override
    public boolean isComplete() {
        for (Phase phase : getPhases()) {
            if (!phase.isComplete()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
