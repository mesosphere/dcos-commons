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
 * Created by gabriel on 10/19/16.
 */
public class DefaultPhaseBuilderTest {
    private static final String phaseName = "test-phase";
    private DefaultPhaseBuilder planBuilder;

    @Mock Block block0;
    @Mock Block block1;
    @Mock Block block2;
    @Mock Block block3;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(block0.getStrategy()).thenReturn(new SerialStrategy<>());
        when(block1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(block2.getStrategy()).thenReturn(new SerialStrategy<>());
        when(block3.getStrategy()).thenReturn(new SerialStrategy<>());

        when(block0.getName()).thenReturn("block0");
        when(block1.getName()).thenReturn("block1");
        when(block2.getName()).thenReturn("block2");
        when(block3.getName()).thenReturn("block3");

        when(block0.isPending()).thenReturn(true);
        when(block1.isPending()).thenReturn(true);
        when(block2.isPending()).thenReturn(true);
        when(block3.isPending()).thenReturn(true);

        planBuilder = new DefaultPhaseBuilder(phaseName);
    }

    @Test
    public void testBuildSerialPlan() {
        planBuilder.addDependency(block2, block1);
        planBuilder.addDependency(block1, block0);
        DefaultPhase phase = planBuilder.build();

        Assert.assertEquals(block0, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        when(block0.isComplete()).thenReturn(true);
        when(block0.isPending()).thenReturn(false);
        Assert.assertEquals(block1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(block1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        when(block1.isComplete()).thenReturn(true);
        when(block1.isPending()).thenReturn(false);
        Assert.assertEquals(block2, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        when(block2.isComplete()).thenReturn(true);
        when(block2.isPending()).thenReturn(false);
        Assert.assertTrue(phase.getStrategy().getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testBuildParallelPlan() throws DependencyStrategyHelper.InvalidDependencyException {
        planBuilder.add(block0);
        planBuilder.add(block1);
        planBuilder.add(block2);
        DefaultPhase phase = planBuilder.build();

        Assert.assertEquals(3, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());

        when(block0.isComplete()).thenReturn(true);
        when(block0.isPending()).thenReturn(false);
        Assert.assertEquals(2, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());

        // Try again, shouldn't change.
        Assert.assertEquals(2, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());

        when(block2.isComplete()).thenReturn(true);
        when(block2.isPending()).thenReturn(false);
        Assert.assertEquals(1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());
        Assert.assertEquals(block1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        when(block1.isComplete()).thenReturn(true);
        when(block1.isPending()).thenReturn(false);
        Assert.assertTrue(phase.getStrategy().getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testBuildDiamondPlan() {
        planBuilder.addDependency(block3, block1);
        planBuilder.addDependency(block3, block2);
        planBuilder.addDependency(block1, block0);
        planBuilder.addDependency(block2, block0);
        DefaultPhase phase = planBuilder.build();

        Assert.assertEquals(1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());
        Assert.assertEquals(block0, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        when(block0.isComplete()).thenReturn(true);
        when(block0.isPending()).thenReturn(false);
        Assert.assertEquals(2, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());

        when(block1.isComplete()).thenReturn(true);
        when(block1.isPending()).thenReturn(false);
        Assert.assertEquals(1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());
        Assert.assertEquals(block2, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        // Try again, shouldn't change.
        Assert.assertEquals(1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());
        Assert.assertEquals(block2, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        when(block2.isComplete()).thenReturn(true);
        when(block2.isPending()).thenReturn(false);
        Assert.assertEquals(1, phase.getStrategy().getCandidates(phase, Collections.emptyList()).size());
        Assert.assertEquals(block3, phase.getStrategy().getCandidates(phase, Collections.emptyList()).iterator().next());

        when(block3.isComplete()).thenReturn(true);
        when(block3.isPending()).thenReturn(false);
        Assert.assertTrue(phase.getStrategy().getCandidates(phase, Collections.emptyList()).isEmpty());
    }
}
