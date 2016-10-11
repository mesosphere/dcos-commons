package org.apache.mesos.scheduler.plan;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RandomRecoveryStrategy}.
 */
public class RandomRecoveryStrategyTest {
    @Test
    public void testGetCurrentBlockNoBlocks() {
        Phase phase = mock(Phase.class);
        when(phase.getBlocks()).thenReturn(Arrays.asList());
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertFalse(randomRecoveryStrategy.getCurrentBlock().isPresent());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockSingleNonCompleteBlock() {
        Phase phase = mock(Phase.class);
        final Block nonCompleteBlock = mock(Block.class);
        final List<? extends Block> blocks = Arrays.asList(nonCompleteBlock);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertTrue(randomRecoveryStrategy.getCurrentBlock().isPresent());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockSingleCompleteBlock() {
        Phase phase = mock(Phase.class);
        final Block completeBlock = mock(Block.class);
        when(completeBlock.isComplete()).thenReturn(true);
        final List<? extends Block> blocks = Arrays.asList(completeBlock);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertFalse(randomRecoveryStrategy.getCurrentBlock().isPresent());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockAllNonCompleteBlock() {
        Phase phase = mock(Phase.class);
        final Block notCompletedA = mock(Block.class);
        final Block notCompletedB = mock(Block.class);
        when(notCompletedA.isComplete()).thenReturn(false);
        when(notCompletedB.isComplete()).thenReturn(false);
        final List<Block> blocks = Arrays.asList(notCompletedA, notCompletedB);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertTrue(randomRecoveryStrategy.getCurrentBlock().isPresent());
    }

    @Test
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockAllCompleteBlock() {
        Phase phase = mock(Phase.class);
        final Block notCompletedA = mock(Block.class);
        final Block notCompletedB = mock(Block.class);
        when(notCompletedA.isComplete()).thenReturn(true);
        when(notCompletedB.isComplete()).thenReturn(true);
        final List<Block> blocks = Arrays.asList(notCompletedA, notCompletedB);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertFalse(randomRecoveryStrategy.getCurrentBlock().isPresent());
    }

    @Test
    public void testFilterOutCompletedBlocksOneCompleted() {
        final Block completed = mock(Block.class);
        final Block notCompleted = mock(Block.class);
        when(completed.isComplete()).thenReturn(true);
        when(notCompleted.isComplete()).thenReturn(false);
        final List<Block> blocks = Arrays.asList(completed, notCompleted);
        final List<Block> filtered = RandomRecoveryStrategy.filterOutCompletedBlocks(blocks);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(1, filtered.size());
    }

    @Test
    public void testFilterOutCompletedBlocksNoCompleted() {
        final Block notCompletedA = mock(Block.class);
        final Block notCompletedB = mock(Block.class);
        when(notCompletedA.isComplete()).thenReturn(false);
        when(notCompletedB.isComplete()).thenReturn(false);
        final List<Block> blocks = Arrays.asList(notCompletedA, notCompletedB);
        final List<Block> filtered = RandomRecoveryStrategy.filterOutCompletedBlocks(blocks);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(2, filtered.size());
    }

    @Test
    public void testFilterOutCompletedBlocksAllCompleted() {
        final Block completedA = mock(Block.class);
        final Block completedB = mock(Block.class);
        when(completedA.isComplete()).thenReturn(true);
        when(completedB.isComplete()).thenReturn(true);
        final List<Block> blocks = Arrays.asList(completedA, completedB);
        final List<Block> filtered = RandomRecoveryStrategy.filterOutCompletedBlocks(blocks);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(0, filtered.size());
    }

    @Test
    public void testFilterOutNull() {
        final List<Block> filtered = RandomRecoveryStrategy.filterOutCompletedBlocks(null);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(0, filtered.size());
    }
}
