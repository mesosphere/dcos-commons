package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TestPodFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.*;

/**
 * This class tests the {@link SafeStrategy}.
 */
public class SafeStrategyTest {
    private TestStep step0;
    private TestStep step1;
    private TestStep step2;
    private TestStep step3;
    private TestStep step4;

    private List<Step> steps;
    private static List<PodInstanceRequirement> podInstanceRequirements = new ArrayList<>();

    @BeforeClass
    public static void beforeAll() {
        for (int i = 0; i < 5; i++) {
            PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
                    .type("type" + i)
                    .count(1)
                    .tasks(Collections.singletonList(TestPodFactory.getTaskSpec()))
                    .build();

            PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
            PodInstanceRequirement podInstanceRequirement =
                    PodInstanceRequirement.newBuilder(podInstance, Collections.singletonList("task0")).build();
            podInstanceRequirements.add(podInstanceRequirement);
        }
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        // (re)initialize as pending:
        step0 = new TestStep("step0", podInstanceRequirements.get(0));
        step1 = new TestStep("step1", podInstanceRequirements.get(1));
        step2 = new TestStep("step2", podInstanceRequirements.get(2));
        step3 = new TestStep("step3", podInstanceRequirements.get(3));
        step4 = new TestStep("step4", podInstanceRequirements.get(4));
        steps = Arrays.asList(step0, step1, step2, step3, step4);
    }

    @Test
    public void testExecution() {
        Strategy<Step> strategy = new SafeStrategy(steps);

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
        markComplete(step2);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the fourth time.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());
        markComplete(step3);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the fifth and final time.
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());
        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSomeAlreadyCompleteExecution() {
        markComplete(step0);
        markComplete(step2);

        Strategy<Step> strategy = new SafeStrategy(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time: step0 is skipped over
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
        markComplete(step3);

        // Proceed to complete final step
        strategy.proceed();
        candidates = getCandidates(steps, strategy);
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());
        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSomeDirtyExecution() {
        Strategy<Step> strategy = new SafeStrategy(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the first time. step0 is only returned if it's not currently dirty.
        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy, podInstanceRequirements.get(0)).isEmpty());
        Collection<Step> candidates = getCandidates(
                steps,
                strategy,
                podInstanceRequirements.get(1),
                podInstanceRequirements.get(2),
                podInstanceRequirements.get(3),
                podInstanceRequirements.get(4));
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step0, candidates.iterator().next());

        markComplete(step0);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        // Proceed the second time. Again, step1 is only returned if it's not dirty.
        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy, podInstanceRequirements.get(1)).isEmpty());
        candidates = getCandidates(
                steps,
                strategy,
                podInstanceRequirements.get(0),
                podInstanceRequirements.get(2),
                podInstanceRequirements.get(3),
                podInstanceRequirements.get(4));
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step1, candidates.iterator().next());

        // Proceed the third time
        markComplete(step1);
        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy, podInstanceRequirements.get(2)).isEmpty());
        candidates = getCandidates(
                steps,
                strategy,
                podInstanceRequirements.get(0),
                podInstanceRequirements.get(1),
                podInstanceRequirements.get(3),
                podInstanceRequirements.get(4));
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step2, candidates.iterator().next());

        markComplete(step2);
        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy, podInstanceRequirements.get(3)).isEmpty());
        candidates = getCandidates(
                steps,
                strategy,
                podInstanceRequirements.get(0),
                podInstanceRequirements.get(1),
                podInstanceRequirements.get(2),
                podInstanceRequirements.get(4));
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step3, candidates.iterator().next());

        markComplete(step3);
        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy, podInstanceRequirements.get(4)).isEmpty());
        candidates = getCandidates(
                steps,
                strategy,
                podInstanceRequirements.get(0),
                podInstanceRequirements.get(1),
                podInstanceRequirements.get(2),
                podInstanceRequirements.get(3));
        Assert.assertEquals(1, candidates.size());
        Assert.assertEquals(step4, candidates.iterator().next());

        markComplete(step4);
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        steps.forEach(element -> Assert.assertTrue(element.isComplete()));
    }

    @Test
    public void testSingleElementExecution() {
        Strategy<Step> strategy = new SafeStrategy(steps);
        Collection<Step> steps = Collections.singletonList(step0);

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
    public void testEmptyExecution() {
        List<Step> steps = Collections.emptyList();
        Strategy<Step> strategy = new SafeStrategy(steps);

        // Initially no candidates should be returned
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());

        strategy.proceed();
        Assert.assertTrue(getCandidates(steps, strategy).isEmpty());
    }

    private static Collection<Step> getCandidates(
            Collection<Step> steps,
            Strategy<Step> strategy,
            PodInstanceRequirement... dirtyAssets) {
        return strategy.getCandidates(steps, Arrays.asList(dirtyAssets));
    }

    private static void markComplete(TestStep step) {
        step.setStatus(Status.COMPLETE);
    }
}
