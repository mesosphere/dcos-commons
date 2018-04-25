package com.mesosphere.sdk.scheduler.plan.strategy;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class StrategyFactoryTest {

    @Test
    public void testParallelPhase() {
        Assert.assertTrue(StrategyFactory.generateForPhase("parallel") instanceof ParallelStrategy);
    }

    @Test
    public void testSerialPhase() {
        Assert.assertTrue(StrategyFactory.generateForPhase("serial") instanceof SerialStrategy);
    }

    @Test
    public void testNullPhase() {
        Assert.assertTrue(StrategyFactory.generateForPhase(null) instanceof SerialStrategy);
    }

    @Test
    public void testParallelStep() {
        Assert.assertTrue(StrategyFactory.generateForSteps("parallel", Collections.emptyList()) instanceof ParallelStrategy);
    }

    @Test
    public void testSerialStep() {
        Assert.assertTrue(StrategyFactory.generateForSteps("serial", Collections.emptyList()) instanceof SerialStrategy);
    }

    @Test
    public void testNullStep() {
        Assert.assertTrue(StrategyFactory.generateForSteps(null, Collections.emptyList()) instanceof SerialStrategy);
    }
}
