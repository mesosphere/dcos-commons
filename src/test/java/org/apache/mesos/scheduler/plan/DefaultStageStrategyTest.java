package org.apache.mesos.scheduler.plan;

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.Arrays;
import java.util.UUID;

/**
 * This class tests the DefaultStageStrategy.
 */
public class DefaultStageStrategyTest {
    private Phase phase;
    private DefaultStageStrategy strategy;


    @Before
    public void beforeEach() {
        phase = DefaultPhase.create(
                UUID.randomUUID(),
                "phase-0",
                Arrays.asList(new TestBlock(), new TestBlock()));

        strategy = new DefaultStageStrategy(phase);
    }

    @Test
    public void testProceedInterrupt() {
        Block block0 = phase.getBlock(0);
        Block block1 = phase.getBlock(1);

        Assert.assertEquals(null, strategy.getCurrentBlock());
        strategy.proceed();
        Assert.assertEquals(block0, strategy.getCurrentBlock());
        strategy.interrupt();
        Assert.assertEquals(block0, strategy.getCurrentBlock());

        block0.setStatus(Status.Complete);
        Assert.assertEquals(null, strategy.getCurrentBlock());
        strategy.proceed();
        Assert.assertEquals(block1, strategy.getCurrentBlock());

        block1.setStatus(Status.Complete);
        Assert.assertEquals(block1, strategy.getCurrentBlock());
    }

    @Test
    public void testRestart() {
        Block block0 = phase.getBlock(0);
        Assert.assertEquals(Status.Pending, block0.getStatus());
        block0.setStatus(Status.Complete);
        Assert.assertEquals(Status.Complete, block0.getStatus());
        strategy.restart(block0.getId());
        Assert.assertEquals(Status.Pending, block0.getStatus());
    }

    @Test
    public void testForceComplete() {
        Block block0 = phase.getBlock(0);
        Assert.assertEquals(Status.Pending, block0.getStatus());
        strategy.forceComplete(block0.getId());
        Assert.assertEquals(Status.Pending, block0.getStatus());
    }

    @Test
    public void testGetStatus() {
        Block block0 = phase.getBlock(0);
        Block block1 = phase.getBlock(1);

        Assert.assertEquals(Status.Waiting, strategy.getStatus());

        strategy.proceed();
        Assert.assertEquals(Status.Pending, strategy.getStatus());
        block0.setStatus(Status.InProgress);
        Assert.assertEquals(Status.InProgress, strategy.getStatus());
        block0.setStatus(Status.Complete);
        Assert.assertEquals(Status.Waiting, strategy.getStatus());

        strategy.proceed();
        Assert.assertEquals(Status.InProgress, strategy.getStatus());
        block1.setStatus(Status.InProgress);
        Assert.assertEquals(Status.InProgress, strategy.getStatus());
        block1.setStatus(Status.Complete);
        Assert.assertEquals(Status.Complete, strategy.getStatus());
    }
}
