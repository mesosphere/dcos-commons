package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.TestStep;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.when;

/**
 * This class tests the {@link ParallelStrategy}.
 */
public class ParallelStrategyTest {
    @Mock Step el0;
    @Mock Step el1;
    @Mock Step el2;

    @Mock private PodInstanceRequirement podInstanceRequirement0;
    @Mock private PodInstanceRequirement podInstanceRequirement1;
    @Mock private PodInstanceRequirement podInstanceRequirement2;

    private ParallelStrategy<Step> strategy;
    private List<Step> steps;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        strategy = new ParallelStrategy<>();

        when(el0.getName()).thenReturn("step0");
        when(el1.getName()).thenReturn("step1");
        when(el2.getName()).thenReturn("step2");

        when(el0.getAsset()).thenReturn(Optional.of(podInstanceRequirement0));
        when(el1.getAsset()).thenReturn(Optional.of(podInstanceRequirement1));
        when(el2.getAsset()).thenReturn(Optional.of(podInstanceRequirement2));

        when(el0.isPending()).thenReturn(true);
        when(el1.isPending()).thenReturn(true);
        when(el2.isPending()).thenReturn(true);

        when(el0.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(true);
        when(el1.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(true);
        when(el2.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(true);

        steps = Arrays.asList(el0, el1, el2);
    }

    @Test
    public void testParallelExecution() {
        Assert.assertEquals(3, getCandidates().size());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(false);
        Assert.assertEquals(2, getCandidates().size());

        when(el1.isComplete()).thenReturn(true);
        when(el1.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(false);
        Assert.assertEquals(1, getCandidates().size());
        Assert.assertEquals(el2, getCandidates().iterator().next());

        when(el2.isComplete()).thenReturn(true);
        when(el2.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(false);
        Assert.assertTrue(getCandidates().isEmpty());
    }

    @Test
    public void testProceedInterrupt() {
        TestStep step0 = new TestStep();
        TestStep step1 = new TestStep();
        List<Step> steps = Arrays.asList(step0, step1);

        Collection<Step> candidates = getCandidates(strategy, steps);
        Assert.assertEquals(2, candidates.size());
        Assert.assertEquals(new HashSet<>(steps), new HashSet<>(candidates));

        strategy.interrupt();
        Assert.assertTrue(getCandidates(strategy, steps).isEmpty());

        strategy.proceed();
        candidates = getCandidates(strategy, steps);
        Assert.assertEquals(2, candidates.size());
        Assert.assertEquals(new HashSet<>(steps), new HashSet<>(candidates));

        step0.setStatus(Status.COMPLETE);
        Assert.assertEquals(step1, getCandidates(strategy, steps).iterator().next());

        strategy.interrupt();
        Assert.assertTrue(getCandidates(strategy, steps).isEmpty());

        strategy.proceed();
        Assert.assertEquals(step1, getCandidates(strategy, steps).iterator().next());

        step1.setStatus(Status.COMPLETE);
        Assert.assertTrue(getCandidates(strategy, steps).isEmpty());

        strategy.interrupt();
        Assert.assertTrue(getCandidates(strategy, steps).isEmpty());
    }

    private Collection<Step> getCandidates() {
        return getCandidates(strategy, steps);
    }

    private static Collection<Step> getCandidates(Strategy<Step> strategy, Collection<Step> steps) {
        return strategy.getCandidates(steps, Collections.emptyList());
    }
}
