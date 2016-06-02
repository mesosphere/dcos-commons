package org.apache.mesos.scheduler.plan;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An ordered list of {@link Phase}s, composed into a {@link Stage}. It may
 * optionally contain a List of errors associated with the phase.
 */
public class DefaultStage implements Stage {

    private final List<? extends Phase> phases;

    private final List<String> errors;

    /**
     * Convenience function for constructing a {@link Stage} from a variable
     * list of arguments.
     * @return A new Stage constructed from the argument phases.
     */
    public static DefaultStage fromArgs(Phase... phases) {
        return fromList(Arrays.asList(phases));
    }

    /**
     * @return {@link Stage} composed of the provided list of {@link Phase}s.
     */
    public static DefaultStage fromList(List<? extends Phase> phases) {
        return new DefaultStage(phases, Collections.<String>emptyList());
    }

    /**
     * @param phases The {@link Phase}s for the stage.
     * @param errors The errors associated with the stage.
     * @return A new Stage containing phases with the associated errors.
     */
    public static DefaultStage withErrors(List<? extends  Phase> phases,
                                          List<String> errors) {
        return new DefaultStage(phases, errors);
    }

    /**
     * Call above helper methods.
     */
    private DefaultStage(final List<? extends Phase> phases,
                         final List<String> errors) {
        this.phases = phases;
        this.errors = errors;
    }

    /**
     * @return The contained list of {@link Phase}s.
     */
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
    public boolean isComplete() {
        for (Phase phase : getPhases()) {
            for (Block block : phase.getBlocks()) {
                if (!block.isComplete()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return True if the phase contains errors.
     */
    public boolean hasErrors(){
        return !errors.isEmpty();
    }
}
