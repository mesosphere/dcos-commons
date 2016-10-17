package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.RandomStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RandomRecoveryStrategy}.
 */
public class RandomRecoveryStrategyTest {
    @Mock private Block pendingBlock;
    @Mock private Block completeBlock;

    private static final Strategy strategy = new RandomStrategy();

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(pendingBlock.getName()).thenReturn("mock-block");
        when(pendingBlock.isPending()).thenReturn(true);
        when(pendingBlock.getStrategy()).thenReturn(strategy);

        when(completeBlock.getName()).thenReturn("mock-block");
        when(completeBlock.isPending()).thenReturn(false);
        when(completeBlock.isComplete()).thenReturn(true);
        when(completeBlock.getStrategy()).thenReturn(strategy);
    }


    @Test
    public void testGetCurrentBlockNoBlocks() {
        Phase phase = mock(Phase.class);
        when(phase.getChildren()).thenReturn(Arrays.asList());
        final Strategy strategy = new RandomStrategy();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockSingleNonCompleteBlock() {
        Phase phase = mock(Phase.class);
        final List<? extends Block> blocks = Arrays.asList(pendingBlock);
        Mockito.doReturn(blocks).when(phase).getChildren();

        final Strategy strategy = new RandomStrategy();
        Assert.assertFalse(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockSingleCompleteBlock() {
        Phase phase = mock(Phase.class);
        final List<? extends Block> blocks = Arrays.asList(completeBlock);
        Mockito.doReturn(blocks).when(phase).getChildren();

        final Strategy strategy = new RandomStrategy();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockAllNonCompleteBlock() {
        Phase phase = mock(Phase.class);
        final List<Block> blocks = Arrays.asList(pendingBlock, pendingBlock);
        Mockito.doReturn(blocks).when(phase).getChildren();

        final Strategy strategy = new RandomStrategy();
        Assert.assertFalse(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockAllCompleteBlock() {
        Phase phase = mock(Phase.class);
        final List<Block> blocks = Arrays.asList(completeBlock, completeBlock);
        Mockito.doReturn(blocks).when(phase).getChildren();
        final Strategy strategy = new RandomStrategy();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }
}
