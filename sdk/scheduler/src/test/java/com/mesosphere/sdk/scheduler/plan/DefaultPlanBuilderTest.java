package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.DependencyStrategyHelper;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.Collections;

import static org.mockito.Mockito.when;

/**
 * This class tests the DefaultPlanBuilder.
 */
public class DefaultPlanBuilderTest {
    private static final String planName = "test-plan";
    private DefaultPlanBuilder planBuilder;

    @Mock
    Phase phase0;
    @Mock Phase phase1;
    @Mock Phase phase2;
    @Mock Phase phase3;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(phase0.getStrategy()).thenReturn(new SerialStrategy<>());
        when(phase1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(phase2.getStrategy()).thenReturn(new SerialStrategy<>());
        when(phase3.getStrategy()).thenReturn(new SerialStrategy<>());

        when(phase0.getName()).thenReturn("step0");
        when(phase1.getName()).thenReturn("step1");
        when(phase2.getName()).thenReturn("step2");
        when(phase3.getName()).thenReturn("step3");

        when(phase0.isPending()).thenReturn(true);
        when(phase1.isPending()).thenReturn(true);
        when(phase2.isPending()).thenReturn(true);
        when(phase3.isPending()).thenReturn(true);

        planBuilder = new DefaultPlanBuilder(planName);
    }

    @Test
    public void testBuildSerialPlan() {
        planBuilder.addDependency(phase2, phase1);
        planBuilder.addDependency(phase1, phase0);
        DefaultPlan plan = planBuilder.build();

        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase0, getCandidates(plan).iterator().next());

        when(phase0.isComplete()).thenReturn(true);
        when(phase0.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase1, getCandidates(plan).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase1, getCandidates(plan).iterator().next());

        when(phase1.isComplete()).thenReturn(true);
        when(phase1.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase2, getCandidates(plan).iterator().next());

        when(phase2.isComplete()).thenReturn(true);
        when(phase2.isPending()).thenReturn(false);
        Assert.assertTrue(getCandidates(plan).isEmpty());
    }

    @Test
    public void testBuildParallelPlan() throws DependencyStrategyHelper.InvalidDependencyException {
        planBuilder.add(phase0);
        planBuilder.add(phase1);
        planBuilder.add(phase2);
        DefaultPlan plan = planBuilder.build();

        Assert.assertEquals(3, getCandidates(plan).size());

        when(phase0.isComplete()).thenReturn(true);
        when(phase0.isPending()).thenReturn(false);
        Assert.assertEquals(2, getCandidates(plan).size());

        // Try again, shouldn't change.
        Assert.assertEquals(2, getCandidates(plan).size());

        when(phase2.isComplete()).thenReturn(true);
        when(phase2.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase1, getCandidates(plan).iterator().next());

        when(phase1.isComplete()).thenReturn(true);
        when(phase1.isPending()).thenReturn(false);
        Assert.assertTrue(getCandidates(plan).isEmpty());
    }

    @Test
    public void testBuildDiamondPlan() {
        planBuilder.addDependency(phase3, phase1);
        planBuilder.addDependency(phase3, phase2);
        planBuilder.addDependency(phase1, phase0);
        planBuilder.addDependency(phase2, phase0);
        DefaultPlan plan = planBuilder.build();

        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase0, getCandidates(plan).iterator().next());

        when(phase0.isComplete()).thenReturn(true);
        when(phase0.isPending()).thenReturn(false);
        Assert.assertEquals(2, getCandidates(plan).size());

        when(phase1.isComplete()).thenReturn(true);
        when(phase1.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase2, getCandidates(plan).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase2, getCandidates(plan).iterator().next());

        when(phase2.isComplete()).thenReturn(true);
        when(phase2.isPending()).thenReturn(false);
        Assert.assertEquals(1, getCandidates(plan).size());
        Assert.assertEquals(phase3, getCandidates(plan).iterator().next());

        when(phase3.isComplete()).thenReturn(true);
        when(phase3.isPending()).thenReturn(false);
        Assert.assertTrue(getCandidates(plan).isEmpty());
    }

    private static Collection<Phase> getCandidates(Plan plan) {
        return plan.getStrategy().getCandidates(plan.getChildren(), Collections.emptyList());
    }
}
