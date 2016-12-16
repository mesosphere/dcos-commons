package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.RandomStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RandomStrategy}.
 */
public class RandomRecoveryStrategyTest {
    @Mock private Step pendingStep;
    @Mock private Step completeStep;

    private static final Strategy<Step> strategy = new RandomStrategy<>();

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(pendingStep.getName()).thenReturn("mock-step");
        when(pendingStep.getAsset()).thenReturn(Optional.of("mock-step"));
        when(pendingStep.isPending()).thenReturn(true);
        when(pendingStep.getStrategy()).thenReturn(strategy);

        when(completeStep.getName()).thenReturn("mock-step");
        when(pendingStep.getAsset()).thenReturn(Optional.of("mock-step"));
        when(completeStep.isPending()).thenReturn(false);
        when(completeStep.isComplete()).thenReturn(true);
        when(completeStep.getStrategy()).thenReturn(strategy);
    }


    @Test
    public void testGetCurrentStepNoSteps() {
        Phase phase = mock(Phase.class);
        when(phase.getChildren()).thenReturn(Arrays.asList());
        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentStepSingleNonPendingStep() {
        Phase phase = mock(Phase.class);
        final List<? extends Step> steps = Arrays.asList(pendingStep);
        Mockito.doReturn(steps).when(phase).getChildren();

        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertFalse(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentStepSinglePendingStep() {
        Phase phase = mock(Phase.class);
        final List<? extends Step> steps = Arrays.asList(completeStep);
        Mockito.doReturn(steps).when(phase).getChildren();

        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentStepAllNonPendingStep() {
        Phase phase = mock(Phase.class);
        final List<Step> steps = Arrays.asList(pendingStep, pendingStep);
        Mockito.doReturn(steps).when(phase).getChildren();

        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertFalse(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentStepAllPendingStep() {
        Phase phase = mock(Phase.class);
        final List<Step> steps = Arrays.asList(completeStep, completeStep);
        Mockito.doReturn(steps).when(phase).getChildren();
        final Strategy<Step> strategy = new RandomStrategy<>();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }
}
