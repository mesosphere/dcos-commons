package org.apache.mesos.scheduler.plan;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
        Assert.assertFalse(randomRecoveryStrategy.getCurrentBlock(Arrays.asList()).isPresent());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockSingleNonPendingBlock() {
        Phase phase = mock(Phase.class);
        final Block nonPendingBlock = mock(Block.class);
        when(nonPendingBlock.isPending()).thenReturn(false);
        final List<? extends Block> blocks = Arrays.asList(nonPendingBlock);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertFalse(randomRecoveryStrategy.getCurrentBlock(Arrays.asList()).isPresent());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockSinglePendingBlock() {
        Phase phase = mock(Phase.class);
        final Block pendingBlock = mock(Block.class);
        when(pendingBlock.isPending()).thenReturn(true);
        final List<? extends Block> blocks = Arrays.asList(pendingBlock);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertTrue(randomRecoveryStrategy.getCurrentBlock(Arrays.asList()).isPresent());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockAllNonPendingBlock() {
        Phase phase = mock(Phase.class);
        final Block notPendingA = mock(Block.class);
        final Block notPendingB = mock(Block.class);
        when(notPendingA.isPending()).thenReturn(false);
        when(notPendingB.isPending()).thenReturn(false);
        final List<Block> blocks = Arrays.asList(notPendingA, notPendingB);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertFalse(randomRecoveryStrategy.getCurrentBlock(Arrays.asList()).isPresent());
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void testGetCurrentBlockAllPendingBlock() {
        Phase phase = mock(Phase.class);
        final Block pendingA = mock(Block.class);
        final Block pendingB = mock(Block.class);
        when(pendingA.isPending()).thenReturn(true);
        when(pendingB.isPending()).thenReturn(true);
        final List<Block> blocks = Arrays.asList(pendingA, pendingB);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertTrue(randomRecoveryStrategy.getCurrentBlock(Arrays.asList()).isPresent());
    }

    @Test
    public void testFilterOnlyPendingBlocksOnePending() {
        final Block pending = mock(Block.class);
        final Block notPending = mock(Block.class);
        when(pending.isPending()).thenReturn(true);
        when(notPending.isPending()).thenReturn(false);
        final List<Block> blocks = Arrays.asList(pending, notPending);
        final List<Block> filtered = RandomRecoveryStrategy.filterOnlyPendingBlocks(blocks);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(1, filtered.size());
    }

    @Test
    public void testFilterOnlyPendingBlocksNoPending() {
        final Block notPendingA = mock(Block.class);
        final Block notPendingB = mock(Block.class);
        when(notPendingA.isPending()).thenReturn(false);
        when(notPendingB.isPending()).thenReturn(false);
        final List<Block> blocks = Arrays.asList(notPendingA, notPendingB);
        final List<Block> filtered = RandomRecoveryStrategy.filterOnlyPendingBlocks(blocks);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(0, filtered.size());
    }

    @Test
    public void testFilterOnlyPendingBlocksAllPending() {
        final Block pendingA = mock(Block.class);
        final Block pendingB = mock(Block.class);
        when(pendingA.isPending()).thenReturn(true);
        when(pendingB.isPending()).thenReturn(true);
        final List<Block> blocks = Arrays.asList(pendingA, pendingB);
        final List<Block> filtered = RandomRecoveryStrategy.filterOnlyPendingBlocks(blocks);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(2, filtered.size());
    }

    @Test
    public void testFilterOutNull() {
        final List<Block> filtered = RandomRecoveryStrategy.filterOnlyPendingBlocks(null);
        Assert.assertNotNull(filtered);
        Assert.assertEquals(0, filtered.size());
    }
}
