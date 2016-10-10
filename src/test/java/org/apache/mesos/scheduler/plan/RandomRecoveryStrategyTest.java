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
    public void testGetCurrentBlockSingleBlock() {
        Phase phase = mock(Phase.class);
        final Block mock = mock(Block.class);
        final List<? extends Block> blocks = Arrays.asList(mock);
        Mockito.doReturn(blocks).when(phase).getBlocks();
        final RandomRecoveryStrategy randomRecoveryStrategy = new RandomRecoveryStrategy(phase);
        Assert.assertEquals(true, randomRecoveryStrategy.getCurrentBlock().isPresent());
    }
}
