package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;

import static org.mockito.Mockito.when;

/**
 * Created by gabriel on 10/20/16.
 */
public class CustomPlanTest {
    @Mock Block block0;
    @Mock Block block1;
    @Mock Block block2;
    @Mock Block block3;

    private Collection<Block> blocks;

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

        when(block0.getStatus()).thenReturn(Status.PENDING);
        when(block1.getStatus()).thenReturn(Status.PENDING);
        when(block2.getStatus()).thenReturn(Status.PENDING);
        when(block3.getStatus()).thenReturn(Status.PENDING);

        when(block0.isPending()).thenReturn(true);
        when(block1.isPending()).thenReturn(true);
        when(block2.isPending()).thenReturn(true);
        when(block3.isPending()).thenReturn(true);

        blocks = Arrays.asList(block0, block1, block2, block3);
    }

    @Test
    public void testCustomPlanBuilding() throws DependencyStrategyHelper.InvalidDependencyException {
        // This test does not validate plan behavior.  See the PhaseBuilder, PlanBuilder, and Strategy tests.
        // This test is a proof of concept validating ease of custom plan construction, similar to the
        // CustomTaskSetTest
        Phase parallelPhase = getParallelPhase();
        Phase diamondPhase = getDiamondPhase();
        Phase serialPhase = getSerialPhase();

        DefaultPlanBuilder planBuilder = new DefaultPlanBuilder("custom");
        planBuilder.addDependency(serialPhase, diamondPhase);
        planBuilder.addDependency(diamondPhase, parallelPhase);

        Plan plan = planBuilder.build();
        Assert.assertNotNull(plan);
    }

    private Phase getParallelPhase() throws DependencyStrategyHelper.InvalidDependencyException {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("parallel");
        phaseBuilder.add(blocks);
        return phaseBuilder.build();
    }

    private Phase getDiamondPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("diamond");
        phaseBuilder.addDependency(block3, block1);
        phaseBuilder.addDependency(block3, block2);
        phaseBuilder.addDependency(block1, block0);
        phaseBuilder.addDependency(block2, block0);

        return phaseBuilder.build();
    }

    private Phase getSerialPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("serial");
        phaseBuilder.addDependency(block3, block2);
        phaseBuilder.addDependency(block2, block1);
        phaseBuilder.addDependency(block1, block0);

        return phaseBuilder.build();
    }
}
