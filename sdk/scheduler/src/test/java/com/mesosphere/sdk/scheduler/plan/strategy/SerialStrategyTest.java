package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.when;

/**
 * This class tests the {@link SerialStrategy}.
 */
public class SerialStrategyTest {
    @Mock Step el0;
    @Mock Step el1;
    @Mock Step el2;
    
    @Mock private PodInstanceRequirement podInstanceRequirement0;
    @Mock private PodInstanceRequirement podInstanceRequirement1;
    @Mock private PodInstanceRequirement podInstanceRequirement2;

    private SerialStrategy<Step> strategy;
    private List<Step> steps;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        strategy = new SerialStrategy<>();

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
    public void testSerialExecution() {
        Assert.assertEquals(1, strategy.getCandidates(steps, Collections.emptyList()).size());
        Assert.assertEquals(el0, strategy.getCandidates(steps, Collections.emptyList()).iterator().next());

        when(el0.isComplete()).thenReturn(true);
        when(el0.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(false);
        Assert.assertEquals(1, strategy.getCandidates(steps, Collections.emptyList()).size());
        Assert.assertEquals(el1, strategy.getCandidates(steps, Collections.emptyList()).iterator().next());

        when(el1.isComplete()).thenReturn(true);
        when(el1.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(false);
        Assert.assertEquals(1, strategy.getCandidates(steps, Collections.emptyList()).size());
        Assert.assertEquals(el2, strategy.getCandidates(steps, Collections.emptyList()).iterator().next());

        when(el2.isComplete()).thenReturn(true);
        when(el2.isEligible(anyCollectionOf(PodInstanceRequirement.class))).thenReturn(false);
        Assert.assertTrue(strategy.getCandidates(steps, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testProceedInterrupt() {
        Phase phase = new DefaultPhase(
                "phase-0",
                Arrays.asList(new TestStep(), new TestStep()),
                strategy,
                Collections.emptyList());

        TestStep step0 = (TestStep)phase.getChildren().get(0);
        TestStep step1 = (TestStep)phase.getChildren().get(1);

        strategy.interrupt();
        Assert.assertTrue(strategy.getCandidates(phase.getChildren(), Collections.emptyList()).isEmpty());

        strategy.proceed();
        Assert.assertEquals(step0,
                strategy.getCandidates(phase.getChildren(), Collections.emptyList()).iterator().next());

        strategy.interrupt();
        Assert.assertTrue(strategy.getCandidates(phase.getChildren(), Collections.emptyList()).isEmpty());

        step0.setStatus(Status.COMPLETE);
        Assert.assertTrue(strategy.getCandidates(phase.getChildren(), Collections.emptyList()).isEmpty());

        strategy.proceed();
        Assert.assertEquals(step1,
                strategy.getCandidates(phase.getChildren(), Collections.emptyList()).iterator().next());

        step1.setStatus(Status.COMPLETE);
        Assert.assertTrue(strategy.getCandidates(phase.getChildren(), Collections.emptyList()).isEmpty());
    }
}
