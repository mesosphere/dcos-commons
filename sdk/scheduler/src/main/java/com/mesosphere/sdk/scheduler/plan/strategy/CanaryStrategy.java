package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.ParentElement;
import com.mesosphere.sdk.scheduler.plan.PlanUtils;
import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CanaryStrategy blocks deployment entirely by default until human intervention through the {@link #proceed()} call.
 * After a single {@link #proceed()} call it again blocks until human intervention.  Any further calls to
 * {@link #proceed()} will indicate the strategy should continue without further intervention.
 *
 * Unlike other strategies, CanaryStrategy may only be applied to {@link com.mesosphere.sdk.scheduler.plan.Phase}s to
 * manage the progress of child {@link Step}s. This is a measure to avoid user/developer error resulting from ambiguity
 * in what should happen when a CanaryStrategy is applied to a {@link com.mesosphere.sdk.scheduler.plan.Plan}. In
 * practice developers and users should be able to get their desired behavior by directly applying CanaryStrategy to
 * some subset of the {@link Phases}s in their plan.
 */
public class CanaryStrategy implements Strategy<Step> {

    /**
     * One proceed() to launch the first block, then a second proceed() to launch all the rest
     */
    private static final int DEFAULT_PROCEED_COUNT = 2;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int requiredProceeds;
    private final Strategy<Step> postCanaryStrategy;

    private List<InterruptableStep> canaryProceedSteps;

    /**
     * Simple struct for keeping track of steps which require a proceed() to get unblocked by a canary operation
     */
    private static class InterruptableStep {
        private final Step step;
        private boolean proceedNeeded;

        private InterruptableStep(Step step) {
            this.step = step;
            this.proceedNeeded = true;
        }
    }

    /**
     * Creates a new Canary Strategy which will require 2 {@link #proceed()} calls from a user before following the
     * provided {@code postCanaryStrategy}.
     *
     * @param postCanaryStrategy the strategy to use after the canary stage has completed
     */
    public CanaryStrategy(Strategy<Step> postCanaryStrategy) {
        this(postCanaryStrategy, DEFAULT_PROCEED_COUNT);
    }

    /**
     * Creates a new Canary Strategy which will require a specified number of {@link #proceed()} calls from a user
     * before following the provided {@code postCanaryStrategy}.
     *
     * @param postCanaryStrategy the strategy to use after the canary stage has completed
     * @param requiredProceeds the number of {@link #proceed()} calls to require before the provided strategy is
     *     executed
     */
    public CanaryStrategy(Strategy<Step> postCanaryStrategy, int requiredProceeds) {
        this.requiredProceeds = requiredProceeds;
        this.postCanaryStrategy = postCanaryStrategy;
    }

    @Override
    public Collection<Step> getCandidates(ParentElement<Step> parentElement, Collection<String> dirtyAssets) {
        if (canaryProceedSteps == null) {
            // Create a list of steps which will first require proceed() calls before the floodgates of default serial
            // behavior are opened.
            // Note that this assumes these steps do not switch state on their own before being returned as candidates:
            // - If a non-candidate step later became in-progress or complete on it's own, we would still require an
            //   explicit proceed() call against it.
            // - Conversely, any steps which are currently in-progress or complete but later revert to pending or
            //   waiting would not require proceed() calls, but they would be executed out of order, following the
            //   canary steps which were originally selected.
            canaryProceedSteps = parentElement.getChildren().stream()
                    .filter(step -> (step.isPending() || step.isWaiting()))
                    .limit(requiredProceeds)
                    .map(step -> new InterruptableStep(step))
                    .collect(Collectors.toList());
        }

        // Manual canary logic: find the first eligible step, or stop if we hit a step that requires a proceed() call.
        for (InterruptableStep interruptableStep : canaryProceedSteps) {
            if (interruptableStep.proceedNeeded) {
                // search reached a step in the list which still requires a canary proceed. stop now.
                return Collections.emptyList();
            } else if (PlanUtils.isEligibleCandidate(interruptableStep.step, dirtyAssets)) {
                return Arrays.asList(interruptableStep.step);
            }
        }

        // Canary steps are all proceedNeeded=false. fall back to default serial logic.
        return postCanaryStrategy.getCandidates(parentElement, dirtyAssets);
    }

    @Override
    public void proceed() {
        if (canaryProceedSteps == null) {
            logger.warn("Proceed has no effect before canary has initialized.");
            return;
        }

        // Update locally interrupted step, or fall back to default proceed()
        InterruptableStep nextCanaryStep = getNextCanaryStep();
        if (nextCanaryStep != null) {
            nextCanaryStep.proceedNeeded = false;
        } else {
            postCanaryStrategy.proceed();
        }
    }

    @Override
    public void interrupt() {
        if (canaryProceedSteps == null) {
            logger.warn("Interrupt has no effect before canary has initialized.");
            return;
        }

        // Only listen to interrupts when the canary operation isn't active. In other words, if we're already
        // 'interrupted' by the canary itself, an explicit interrupt() call from the user should be a no-op.
        if (getNextCanaryStep() != null) {
            logger.warn("Interrupt has no effect before canary has completed.");
        } else {
            postCanaryStrategy.interrupt();
        }
    }

    @Override
    public boolean isInterrupted() {
        return getNextCanaryStep() != null || postCanaryStrategy.isInterrupted();
    }

    private InterruptableStep getNextCanaryStep() {
        if (canaryProceedSteps == null) {
            return null;
        }
        for (InterruptableStep step : canaryProceedSteps) {
            if (step.proceedNeeded) {
                return step;
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

        /**
         * Creates a new generator which will require 2 {@link #proceed()} calls from a user before following the
         * provided {@code postCanaryStrategy}.
         *
         * @param postCanaryStrategy the strategy to use after the canary stage has completed
         */
        public Generator(Strategy<Step> postCanaryStrategy) {
            this(postCanaryStrategy, DEFAULT_PROCEED_COUNT);
        }

        /**
         * Creates a new generator which will require a specified number of {@link #proceed()} calls from a user before
         * following the provided {@code postCanaryStrategy}.
         *
         * @param postCanaryStrategy the strategy to use after the canary stage has completed
         * @param requiredProceeds the number of {@link #proceed()} calls to require before the provided strategy is
         *     executed
         */
        public Generator(Strategy<Step> postCanaryStrategy, int requiredProceeds) {
            this.requiredProceeds = requiredProceeds;
            this.postCanaryStrategy = postCanaryStrategy;
        }

        @Override
        public Strategy<Step> generate() {
            return new CanaryStrategy(postCanaryStrategy, requiredProceeds);
        }
    }
}
