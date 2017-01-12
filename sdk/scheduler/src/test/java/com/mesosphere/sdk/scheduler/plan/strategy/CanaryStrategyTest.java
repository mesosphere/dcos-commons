package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.TestStep;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * This class tests the {@link CanaryStrategy}.
 */
public class CanaryStrategyTest {
    private TestStep step0;
    private TestStep step1;
    private TestStep step2;
    private TestStep step3;
    private TestStep step4;

    private List<Step> steps;

    @Before
    public void beforeEach() {
        // (re)initialize as pending:
        step0 = new TestStep("step0");
        step1 = new TestStep("step1");
        step2 = new TestStep("step2");
        step3 = new TestStep("step3");
        step4 = new TestStep("step4");
        steps = Arrays.asList(step0, step1, step2, step3, step4);
    }

    @Test
    public void testSerialCanaryExecution() {
        Strategy<Step> strategy = new CanaryStrategy(new SerialStrategy<>());

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the second time.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention.
        markComplete(step1);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        markComplete(step2);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testLongSerialCanaryExecution() {
        Strategy<Step> strategy = new CanaryStrategy(new SerialStrategy<>(), 3);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the second time.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        markComplete(step1);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the third time.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        // After step2 completes the rest should roll out without intervention.
        markComplete(step2);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testParallelCanaryExecution() {
        CanaryStrategy strategy = new CanaryStrategy(new ParallelStrategy<>());

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time. Now step0 is a candidate.
        strategy.proceed();
        Collection<Step> candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the second time. Now step1...stepN are all candidates.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(4, candidates.size());
        Assert.assertEquals(new HashSet<>(Arrays.asList(step1, step2, step3, step4)), new HashSet<>(candidates));

        // After some are marked complete, the remainder should be candidates.
        markComplete(step2);
        markComplete(step4);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(2, candidates.size());
        Assert.assertEquals(new HashSet<>(Arrays.asList(step1, step3)), new HashSet<>(candidates));

        markComplete(step1);
        markComplete(step3);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testLongParallelCanaryExecution() {
        CanaryStrategy strategy = new CanaryStrategy(new ParallelStrategy<>(), 3);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time. Now step0 is a candidate.
        strategy.proceed();
        Collection<Step> candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the second time. Now step1 is a candidate.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        markComplete(step1);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Finally, after the third required proceed, step2...stepN are all candidates.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(3, candidates.size());
        Assert.assertEquals(new HashSet<>(Arrays.asList(step2, step3, step4)), new HashSet<>(candidates));

        // After some are marked complete, the remainder should be candidates.
        markComplete(step2);
        markComplete(step4);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testManualInterruptCanaryExecution() {
        Strategy<Step> strategy = new CanaryStrategy(new SerialStrategy<>());

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // This interrupt should be ignored, no extra proceeds needed to continue canary
        strategy.interrupt();

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // This interrupt should be ignored, no extra proceeds needed to continue canary
        strategy.interrupt();

        // Proceed the second time.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention and interrupt should work.
        // This interrupt should be passed to the underlying SerialStrategy which will refuse to return step2
        strategy.interrupt();
        markComplete(step1);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // When we proceed step2 is now eligible again
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        // This interrupt should be passed to the underlying SerialStrategy which will refuse to return step3
        strategy.interrupt();
        markComplete(step2);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // When we proceed step3 is now eligible again
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSomeAlreadyCompleteCanaryExecution() {
        // These steps should be totally skipped over by the canary:
        markComplete(step0, step2);

        Strategy<Step> strategy = new CanaryStrategy(new SerialStrategy<>());

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time: step0 is skipped over by the canary
        strategy.proceed();
        Collection<Step> candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        markComplete(step1);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the second time: step2 is skipped over
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention.
        markComplete(step3);
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSomeDirtyCanaryExecution() {
        Strategy<Step> strategy = new CanaryStrategy(new SerialStrategy<>());

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time. step0 is only returned if it's not currently dirty.
        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy, "step0").isEmpty());
        Collection<Step> candidates = getCandidates(steps, strategy, "step1", "step2", "step3", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the second time. Again, step1 is only returned if it's not dirty.
        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy, "step1").isEmpty());
        candidates = getCandidates(steps, strategy, "step0", "step2", "step3", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention.
        markComplete(step1);
        Assert.assertTrue(getCandidates(steps, strategy, "step2").isEmpty());
        candidates = getCandidates(steps, strategy, "step0", "step1", "step3", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        markComplete(step2);
        Assert.assertTrue(getCandidates(steps, strategy, "step3").isEmpty());
        candidates = getCandidates(steps, strategy, "step0", "step1", "step2", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        Assert.assertTrue(getCandidates(steps, strategy, "step4").isEmpty());
        candidates = getCandidates(steps, strategy, "step0", "step1", "step2", "step3");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSingleElementCanaryExecution() {
        Strategy<Step> strategy = new CanaryStrategy(new SerialStrategy<>());
        Collection<Step> steps = Arrays.asList(step0);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testEmptyCanaryExecution() {
        Strategy<Step> strategy = new CanaryStrategy(new SerialStrategy<>());
        Collection<Step> steps = Collections.emptyList();

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());
    }

    private static Collection<Step> getCandidates(
            Collection<Step> steps, Strategy<Step> strategy, String... dirtyAssets) {
        return strategy.getCandidates(steps, Arrays.asList(dirtyAssets));
    }

    private static void markComplete(TestStep... steps) {
        for (TestStep step : steps) {
            step.setStatus(Status.COMPLETE);
        }
    }
}
