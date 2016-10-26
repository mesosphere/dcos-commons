package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategyHelper;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.mockito.Mockito.when;

/**
 * This class tests the DefaultPlanBuilder.
 */
public class DefaultPlanBuilderTest {
    private static final String planName = "test-plan";
    private DefaultPlanBuilder planBuilder;

    @Mock Phase phase0;
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

        when(phase0.getName()).thenReturn("block0");
        when(phase1.getName()).thenReturn("block1");
        when(phase2.getName()).thenReturn("block2");
        when(phase3.getName()).thenReturn("block3");

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

        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase0, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        when(phase0.isComplete()).thenReturn(true);
        when(phase0.isPending()).thenReturn(false);
        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        when(phase1.isComplete()).thenReturn(true);
        when(phase1.isPending()).thenReturn(false);
        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase2, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        when(phase2.isComplete()).thenReturn(true);
        when(phase2.isPending()).thenReturn(false);
        Assert.assertTrue(plan.getStrategy().getCandidates(plan, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testBuildParallelPlan() throws DependencyStrategyHelper.InvalidDependencyException {
        planBuilder.addAll(phase0);
        planBuilder.addAll(phase1);
        planBuilder.addAll(phase2);
        DefaultPlan plan = planBuilder.build();

        Assert.assertEquals(3, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());

        when(phase0.isComplete()).thenReturn(true);
        when(phase0.isPending()).thenReturn(false);
        Assert.assertEquals(2, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());

        // Try again, shouldn't change.
        Assert.assertEquals(2, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());

        when(phase2.isComplete()).thenReturn(true);
        when(phase2.isPending()).thenReturn(false);
        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        when(phase1.isComplete()).thenReturn(true);
        when(phase1.isPending()).thenReturn(false);
        Assert.assertTrue(plan.getStrategy().getCandidates(plan, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testBuildDiamondPlan() {
        planBuilder.addDependency(phase3, phase1);
        planBuilder.addDependency(phase3, phase2);
        planBuilder.addDependency(phase1, phase0);
        planBuilder.addDependency(phase2, phase0);
        DefaultPlan plan = planBuilder.build();

        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase0, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        when(phase0.isComplete()).thenReturn(true);
        when(phase0.isPending()).thenReturn(false);
        Assert.assertEquals(2, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());

        when(phase1.isComplete()).thenReturn(true);
        when(phase1.isPending()).thenReturn(false);
        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase2, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase2, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        when(phase2.isComplete()).thenReturn(true);
        when(phase2.isPending()).thenReturn(false);
        Assert.assertEquals(1, plan.getStrategy().getCandidates(plan, Collections.emptyList()).size());
        Assert.assertEquals(phase3, plan.getStrategy().getCandidates(plan, Collections.emptyList()).iterator().next());

        when(phase3.isComplete()).thenReturn(true);
        when(phase3.isPending()).thenReturn(false);
        Assert.assertTrue(plan.getStrategy().getCandidates(plan, Collections.emptyList()).isEmpty());
    }
}
