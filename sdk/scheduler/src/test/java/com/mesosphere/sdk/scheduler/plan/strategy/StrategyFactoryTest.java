package com.mesosphere.sdk.scheduler.plan.strategy;

import org.junit.Test;
import org.springframework.util.Assert;

public class StrategyFactoryTest {

    @Test
    public void testParallelPhase() {
        Assert.isInstanceOf(ParallelStrategy.class, StrategyFactory.generateForPhase("parallel"));
    }

    @Test
    public void testSerialPhase() {
        Assert.isInstanceOf(SerialStrategy.class, StrategyFactory.generateForPhase("serial"));
    }

    @Test
    public void testNullPhase() {
        Assert.isInstanceOf(SerialStrategy.class, StrategyFactory.generateForPhase(null));
    }

    @Test
    public void testParallelStep() {
        Assert.isInstanceOf(ParallelStrategy.class, StrategyFactory.generateForSteps("parallel"));
    }

    @Test
    public void testSerialStep() {
        Assert.isInstanceOf(SerialStrategy.class, StrategyFactory.generateForSteps("serial"));
    }

    @Test
    public void testNullStep() {
        Assert.isInstanceOf(SerialStrategy.class, StrategyFactory.generateForSteps(null));
    }
}
