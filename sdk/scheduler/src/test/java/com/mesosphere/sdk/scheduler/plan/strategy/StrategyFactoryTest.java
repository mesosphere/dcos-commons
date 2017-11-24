package com.mesosphere.sdk.scheduler.plan.strategy;

import org.junit.Test;
import org.springframework.util.Assert;

import java.util.Collections;

public class StrategyFactoryTest {
    private StrategyFactory factory = new StrategyFactory();

    @Test
    public void testParallelPhase() {
        Assert.isInstanceOf(ParallelStrategy.class, factory.generateForPhase("parallel"));
    }

    @Test
    public void testSerialPhase() {
        Assert.isInstanceOf(SerialStrategy.class, factory.generateForPhase("serial"));
    }

    @Test
    public void testNullPhase() {
        Assert.isInstanceOf(SerialStrategy.class, factory.generateForPhase(null));
    }

    @Test
    public void testParallelStep() {
        Assert.isInstanceOf(
                ParallelStrategy.class,
                factory.generateForSteps("parallel", Collections.emptyList()));
    }

    @Test
    public void testSerialStep() {
        Assert.isInstanceOf(SerialStrategy.class, factory.generateForSteps("serial", Collections.emptyList()));
    }

    @Test
    public void testNullStep() {
        Assert.isInstanceOf(SerialStrategy.class, factory.generateForSteps(null, Collections.emptyList()));
    }
}
