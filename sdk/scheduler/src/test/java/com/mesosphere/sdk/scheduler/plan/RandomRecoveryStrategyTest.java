package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.RandomStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RandomStrategy}.
 */
public class RandomRecoveryStrategyTest {
    @Mock private Step pendingStep;
    @Mock private Step completeStep;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(pendingStep.getName()).thenReturn("mock-step");
        when(pendingStep.getAsset()).thenReturn(Optional.of("pending-step"));
        when(pendingStep.isPending()).thenReturn(true);
        when(pendingStep.isEligible(any(Collection.class))).thenReturn(true);

        when(completeStep.getName()).thenReturn("mock-step");
        when(completeStep.getAsset()).thenReturn(Optional.of("complete-step"));
        when(completeStep.isPending()).thenReturn(false);
        when(completeStep.isComplete()).thenReturn(true);
        when(completeStep.isEligible(any(Collection.class))).thenReturn(false);
    }


    @Test
    public void testGetCurrentStepNoSteps() {
        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertTrue(strategy.getCandidates(Collections.emptyList(), Collections.emptyList()).isEmpty());
    }

    @Test
    public void testGetCurrentStepSingleNonPendingStep() {
        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertFalse(strategy.getCandidates(Arrays.asList(pendingStep), Collections.emptyList()).isEmpty());
    }

    @Test
    public void testGetCurrentStepSinglePendingStep() {
        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertTrue(strategy.getCandidates(Arrays.asList(completeStep), Collections.emptyList()).isEmpty());
    }

    @Test
    public void testGetCurrentStepAllNonPendingStep() {
        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertFalse(strategy.getCandidates(
                Arrays.asList(pendingStep, pendingStep), Collections.emptyList()).isEmpty());
    }

    @Test
    public void testGetCurrentStepAllPendingStep() {
        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertTrue(strategy.getCandidates(
                Arrays.asList(completeStep, completeStep), Collections.emptyList()).isEmpty());
    }
}
