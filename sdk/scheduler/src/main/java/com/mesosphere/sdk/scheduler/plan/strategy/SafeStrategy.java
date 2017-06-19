package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Interruptible;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A SafeStrategy requires that the user manually invoke {@link #proceed()} calls in order to move along a deployment.
 * This allows the user to manually check that the rollout looks good at each Step.
 * <p>
 * The default is to do nothing until a first {@link #proceed()} call is received, at which point the first {@link Step}
 * is deployed. The user may then examine whether the deployment of that first {@link Step} was successful before
 * invoking the next {@link #proceed()} call, continuing until the deployment is complete.
 */
public class SafeStrategy implements Strategy<Step> {

    private Collection<Step> safeSteps;

    /**
     * Creates a new SafeStrategy based on the Steps given, requiring {@link #proceed()} calls from a user in order for
     * each Step to complete. Initially all pending steps are interrupted.
     *
     * @param steps The child {@link Step}s to complete
     */
    public SafeStrategy(List<Step> steps) {
        this.safeSteps = buildSafeSteps(steps);
    }

    private List<Step> buildSafeSteps(List<Step> steps) {
        return steps.stream()
                .filter(this::isIncomplete)
                .peek(this::interruptPendingStep)
                .collect(Collectors.toList());
    }

    private boolean isIncomplete(Step step) {
        return step.isPending() || step.isInterrupted();
    }

    private void interruptPendingStep(Step step) {
        if (step.isPending()) {
            step.interrupt();
        }
    }

    @Override
    public Collection<Step> getCandidates(Collection<Step> steps, Collection<PodInstanceRequirement> dirtyAssets) {
        return safeSteps.stream()
                .filter(step -> step.isEligible(dirtyAssets))
                .collect(Collectors.toList());
    }

    @Override
    public void interrupt() {
    }

    @Override
    public void proceed() {
        getNextInterruptedStep().ifPresent(Interruptible::proceed);
    }

    @Override
    public boolean isInterrupted() {
        // 1 or more Steps is interrupted and 1 or more Steps is ready to proceed
        return (getNextInterruptedStep().isPresent() && hasNextProceedStep());
    }

    private boolean hasNextProceedStep() {
        return safeSteps.stream().anyMatch(this::readyToProceed);
    }

    private Optional<Step> getNextInterruptedStep() {
        return safeSteps.stream().filter(Interruptible::isInterrupted).findFirst();
    }

    private boolean readyToProceed(Step step) {
        return !step.isInterrupted() && !step.isComplete();
    }

}