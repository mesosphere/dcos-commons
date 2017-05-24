package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A CanaryStrategy is a gatekeeper around another underlying strategy. It requires that the user manually invoke
 * {@link #proceed()} calls in order to move along a deployment. This allows the user to manually check that the
 * deployment looks good before a full rollout is attempted.
 *
 * The default is to do nothing until a first {@link #proceed()} call is received, at which point the first {@link Step}
 * is deployed. The user may then examine whether the deployment of that first {@link Step} was successful before
 * invoking a {@link #proceed()} call. By default this second {@link #proceed()} will result in deploying all the other
 * {@link Step}s according to the rules of the underlying strategy. The number of {@link #proceed()} calls required to
 * enter this state may be customized via the CanaryStrategy constructor.
 *
 * Unlike other strategies, CanaryStrategy may only be applied to {@link com.mesosphere.sdk.scheduler.plan.Phase}s to
 * manage the progress of child {@link Step}s. This requirement avoids user or developer error resulting from ambiguity
 * in what should happen when e.g. a CanaryStrategy is applied to a {@link com.mesosphere.sdk.scheduler.plan.Plan}
 * against child {@link com.mesosphere.sdk.scheduler.plan.Phase}s. In practice, developers and users should be able to
 * get their desired behavior by directly applying CanaryStrategy to some subset of the
 * {@link com.mesosphere.sdk.scheduler.plan.Phase}s in their {@link com.mesosphere.sdk.scheduler.plan.Plan}.
 */
public class CanaryStrategy implements Strategy<Step> {

    /**
     * One proceed() to launch the first block, then a second proceed() to launch all the rest.
     */
    private static final int DEFAULT_PROCEED_COUNT = 2;

    private final int requiredProceeds;
    private final Strategy<Step> strategy;

    public Collection<Step> canarySteps;

    /**
     * Creates a new Canary Strategy which will require 2 {@link #proceed()} calls from a user before following the
     * provided {@code postCanaryStrategy}.
     *
     * @param postCanaryStrategy the strategy to use after the canary stage has completed
     */
    public CanaryStrategy(Strategy<Step> postCanaryStrategy, List<Step> steps) {
        this(postCanaryStrategy, DEFAULT_PROCEED_COUNT, steps);
    }

    /**
     * Creates a new Canary Strategy which will require a specified number of {@link #proceed()} calls from a user
     * before following the provided {@code postCanaryStrategy}.
     *
     * @param postCanaryStrategy the strategy to use after the canary stage has completed
     * @param requiredProceeds the number of {@link #proceed()} calls to require before the provided strategy is
     *     executed
     */
    public CanaryStrategy(Strategy<Step> postCanaryStrategy, int requiredProceeds, List<Step> steps) {
        this.requiredProceeds = requiredProceeds;
        this.strategy = postCanaryStrategy;
        this.canarySteps = interruptCanarySteps(steps);
    }

    private List<Step> interruptCanarySteps(List<Step> steps) {
        List<Step> canarySteps = steps.stream()
                .filter(step -> (step.isPending() || step.isInterrupted()))
                .limit(requiredProceeds)
                .collect(Collectors.toList());
        canarySteps.forEach(step -> step.interrupt());
        return canarySteps;
    }

    @Override
    public Collection<Step> getCandidates(Collection<Step> steps, Collection<PodInstanceRequirement> dirtyAssets) {
        if (getNextCanaryStep() != null) {
            // Still in canary. Only return subset of canary steps which are now eligible due to proceed() calls.
            return canarySteps.stream()
                    .filter(step -> step.isEligible(dirtyAssets))
                    .collect(Collectors.toList());
        }

        // Canary period has ended. Revert to underlying strategy for the rest of the operation.
        return strategy.getCandidates(steps, dirtyAssets);
    }

    @Override
    public void interrupt() {
        Step canaryStep = getNextCanaryStep();
        if (canaryStep != null) {
            // Ignoring interrupt as we are still in canary and therefore already interrupted.
            return;
        }
        strategy.interrupt();
    }

    @Override
    public void proceed() {
        Step canaryStep = getNextCanaryStep();
        if (canaryStep != null) {
            canaryStep.proceed();
            return;
        }
        strategy.proceed();
    }

    @Override
    public boolean isInterrupted() {
        if (getNextCanaryStep() != null && getNextProceedStep() == null) {
            return true;
        }
        return strategy.isInterrupted();
    }

    private Step getNextProceedStep() {
        for (Step proceedStep : canarySteps) {
            if (!proceedStep.isInterrupted() && !proceedStep.isComplete()) {
                return proceedStep;
            }
        }
        return null;
    }

    private Step getNextCanaryStep() {
        for (Step canaryStep : canarySteps) {
            if (canaryStep.isInterrupted()) {
                return canaryStep;
            }
        }
        return null;
    }

    /**
     * This class generates Strategy objects of the appropriate type.
     *
     * @param <C> is the type of {@link Element}s to which the Strategy applies.
     */
    public static class Generator implements StrategyGenerator<Step> {

        private final int requiredProceeds;
        private final Strategy<Step> postCanaryStrategy;
        private final List<Step> steps;

        /**
         * Creates a new generator which will require 2 {@link #proceed()} calls from a user before following the
         * provided {@code postCanaryStrategy}.
         *
         * @param postCanaryStrategy the strategy to use after the canary stage has completed
         */
        public Generator(Strategy<Step> postCanaryStrategy, List<Step> steps) {
            this(postCanaryStrategy, DEFAULT_PROCEED_COUNT, steps);
        }

        /**
         * Creates a new generator which will require a specified number of {@link #proceed()} calls from a user before
         * following the provided {@code postCanaryStrategy}.
         *
         * @param postCanaryStrategy the strategy to use after the canary stage has completed
         * @param requiredProceeds the number of {@link #proceed()} calls to require before the provided strategy is
         *     executed
         */
        public Generator(Strategy<Step> postCanaryStrategy, int requiredProceeds, List<Step> steps) {
            this.requiredProceeds = requiredProceeds;
            this.postCanaryStrategy = postCanaryStrategy;
            this.steps = steps;
        }

        @Override
        public Strategy<Step> generate() {
            return new CanaryStrategy(postCanaryStrategy, requiredProceeds, steps);
        }
    }
}
