package org.apache.mesos.scheduler.plan;

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.Arrays;
import java.util.UUID;

/**
 * This class tests the {@link DefaultPhaseStrategy}.
 */
public class DefaultPlanStrategyTest {
    private Phase phase;
    private DefaultPhaseStrategy strategy;

    @Before
    public void beforeEach() {
        phase = DefaultPhase.create(
                UUID.randomUUID(),
                "phase-0",
                Arrays.asList(new TestBlock(), new TestBlock()));

        strategy = new DefaultPhaseStrategy(phase);
    }

    @Test
    public void testProceedInterrupt() {
        TestBlock block0 = (TestBlock)phase.getBlock(0);
        TestBlock block1 = (TestBlock)phase.getBlock(1);

        Assert.assertTrue(!strategy.getCurrentBlock(Arrays.asList()).isPresent());
        strategy.proceed();
        Assert.assertEquals(block0, strategy.getCurrentBlock(Arrays.asList()).get());
        strategy.interrupt();
        Assert.assertEquals(block0, strategy.getCurrentBlock(Arrays.asList()).get());

        block0.setStatus(Status.COMPLETE);
        Assert.assertTrue(!strategy.getCurrentBlock(Arrays.asList()).isPresent());
        strategy.proceed();
        Assert.assertEquals(block1, strategy.getCurrentBlock(Arrays.asList()).get());

        block1.setStatus(Status.COMPLETE);
        Assert.assertEquals(block1, strategy.getCurrentBlock(Arrays.asList()).get());
    }

    @Test
    public void testRestart() {
        TestBlock block0 = (TestBlock)phase.getBlock(0);
        Assert.assertTrue(block0.isPending());
        block0.setStatus(Status.COMPLETE);
        Assert.assertTrue(block0.isComplete());
        strategy.restart(block0.getId());
        Assert.assertTrue(block0.isPending());
    }

    @Test
    public void testForceComplete() {
        Block block0 = phase.getBlock(0);
        Assert.assertTrue(block0.isPending());
        strategy.forceComplete(block0.getId());
        Assert.assertTrue(block0.isPending());
    }

    @Test
    public void testGetStatus() {
        TestBlock block0 = (TestBlock)phase.getBlock(0);
        TestBlock block1 = (TestBlock)phase.getBlock(1);

        Assert.assertEquals(Status.WAITING, strategy.getStatus());

        strategy.proceed();
        Assert.assertEquals(Status.PENDING, strategy.getStatus());
        block0.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, strategy.getStatus());
        block0.setStatus(Status.COMPLETE);
        Assert.assertEquals(Status.WAITING, strategy.getStatus());

        strategy.proceed();
        Assert.assertEquals(Status.IN_PROGRESS, strategy.getStatus());
        block1.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, strategy.getStatus());
        block1.setStatus(Status.COMPLETE);
        Assert.assertEquals(Status.COMPLETE, strategy.getStatus());
    }
}
