package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collections;

/**
 * This class tests the {@link SerialStrategy}.
 */
public class SerialStrategyTest {
    private Phase phase;
    private Strategy strategy = new SerialStrategy();

    @Before
    public void beforeEach() {
        phase = new DefaultPhase(
                "phase-0",
                Arrays.asList(new TestBlock(), new TestBlock()),
                strategy,
                Collections.emptyList());
    }

    @Test
    public void testProceedInterrupt() {
        TestBlock block0 = (TestBlock)phase.getChildren().get(0);
        TestBlock block1 = (TestBlock)phase.getChildren().get(1);

        phase.getStrategy().interrupt();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());

        phase.getStrategy().proceed();
        Assert.assertEquals(block0, strategy.getCandidates(phase, Collections.emptyList()).iterator().next());

        phase.getStrategy().interrupt();
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());

        block0.setStatus(Status.COMPLETE);
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());

        phase.getStrategy().proceed();
        Assert.assertEquals(block1, strategy.getCandidates(phase, Collections.emptyList()).iterator().next());

        block1.setStatus(Status.COMPLETE);
        Assert.assertTrue(strategy.getCandidates(phase, Collections.emptyList()).isEmpty());
    }
}
