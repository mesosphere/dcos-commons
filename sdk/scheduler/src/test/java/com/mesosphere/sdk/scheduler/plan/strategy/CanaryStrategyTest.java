package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Step;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * This class tests the {@link CanaryStrategy}.
 */
public class CanaryStrategyTest {
    @Mock private Phase phase;
    @Mock private Step step0;
    @Mock private Step step1;
    @Mock private Step step2;
    @Mock private Step step3;
    @Mock private Step step4;

    private List<Step> steps;
    private CanaryStrategy strategy;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        steps = Arrays.asList(step0, step1, step2, step3, step4);
        for (int i = 0; i < steps.size(); ++i) {
            Step mockStep = steps.get(i);
            String name = "step" + String.valueOf(i); // "step0" ... "stepN"
            when(mockStep.getName()).thenReturn(name);
            when(mockStep.getAsset()).thenReturn(Optional.of(name));
            // isPending and isWaiting should both work the same:
            if (i % 2 == 0) {
                when(mockStep.isPending()).thenReturn(true);
            } else {
                when(mockStep.isWaiting()).thenReturn(true);
            }
        }
        strategy = new CanaryStrategy(new SerialStrategy<>());
    }

    @Test
    public void testCanaryExecution() {
        when(phase.getChildren()).thenReturn(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the second time.
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention.
        markComplete(step1);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        markComplete(step2);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates().isEmpty());

        phase.getChildren().forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testLongCanaryExecution() {
        strategy = new CanaryStrategy(new SerialStrategy<>(), 3);
        when(phase.getChildren()).thenReturn(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the second time.
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        markComplete(step1);
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the third time.
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        // After step2 completes the rest should roll out without intervention.
        markComplete(step2);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates().isEmpty());

        phase.getChildren().forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testParallelCanaryExecution() {
        strategy = new CanaryStrategy(new ParallelStrategy<>());
        when(phase.getChildren()).thenReturn(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the second time.
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // After step1 completes the rest should roll out (all at once) without intervention.
        markComplete(step1);
        candidates = getCandidates();
        Assert.assertEquals(3, candidates.size());
        Assert.assertEquals(new HashSet<>(Arrays.asList(step2, step3, step4)), new HashSet<>(candidates));

        markComplete(step2);
        markComplete(step4);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        Assert.assertTrue(getCandidates().isEmpty());

        phase.getChildren().forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testManualInterruptCanaryExecution() {
        when(phase.getChildren()).thenReturn(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        // This interrupt should be ignored, no extra proceeds needed to continue canary
        strategy.interrupt();

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates().isEmpty());

        // This interrupt should be ignored, no extra proceeds needed to continue canary
        strategy.interrupt();

        // Proceed the second time.
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention and interrupt should work.
        // This interrupt should be passed to the underlying SerialStrategy which will refuse to return step2
        strategy.interrupt();
        markComplete(step1);
        Assert.assertTrue(getCandidates().isEmpty());

        // When we proceed step2 is now eligible again
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        // This interrupt should be passed to the underlying SerialStrategy which will refuse to return step3
        strategy.interrupt();
        markComplete(step2);
        Assert.assertTrue(getCandidates().isEmpty());

        // When we proceed step3 is now eligible again
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates().isEmpty());

        phase.getChildren().forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSomeAlreadyCompleteCanaryExecution() {
        // These steps should be totally skipped over by the canary:
        markComplete(step0, step2);

        when(phase.getChildren()).thenReturn(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the first time: step0 is skipped over by the canary
        strategy.proceed();
        Collection<Step> candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        markComplete(step1);
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the second time: step2 is skipped over
        strategy.proceed();
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention.
        markComplete(step3);
        candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates().isEmpty());

        phase.getChildren().forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSomeDirtyCanaryExecution() {
        when(phase.getChildren()).thenReturn(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the first time. step0 is only returned if it's not currently dirty.
        strategy.proceed();
        Assert.assertTrue(getCandidates("step0").isEmpty());
        Collection<Step> candidates = getCandidates("step1", "step2", "step3", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the second time. Again, step1 is only returned if it's not dirty.
        strategy.proceed();
        Assert.assertTrue(getCandidates("step1").isEmpty());
        candidates = getCandidates("step0", "step2", "step3", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // After step1 completes the rest should roll out without intervention.
        markComplete(step1);
        Assert.assertTrue(getCandidates("step2").isEmpty());
        candidates = getCandidates("step0", "step1", "step3", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        markComplete(step2);
        Assert.assertTrue(getCandidates("step3").isEmpty());
        candidates = getCandidates("step0", "step1", "step2", "step4");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        Assert.assertTrue(getCandidates("step4").isEmpty());
        candidates = getCandidates("step0", "step1", "step2", "step3");
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates().isEmpty());

        phase.getChildren().forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSingleElementCanaryExecution() {
        when(phase.getChildren()).thenReturn(Arrays.asList(step0));

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        // Proceed the first time.
        strategy.proceed();
        Collection<Step> candidates = getCandidates();
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates().isEmpty());

        phase.getChildren().forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testEmptyCanaryExecution() {
        when(phase.getChildren()).thenReturn(Collections.emptyList());

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates().isEmpty());

        strategy.proceed();
        Assert.assertTrue(getCandidates().isEmpty());
    }

    private Collection<Step> getCandidates(String... dirtyAssets) {
        return strategy.getCandidates(phase, Arrays.asList(dirtyAssets));
    }

    private static void markComplete(Element... elements) {
        for (Element element : elements) {
            when(element.isComplete()).thenReturn(true);
            when(element.isPending()).thenReturn(false);
            when(element.isWaiting()).thenReturn(false);
        }
    }
}
