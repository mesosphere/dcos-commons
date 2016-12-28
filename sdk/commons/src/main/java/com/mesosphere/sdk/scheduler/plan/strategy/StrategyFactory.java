package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Step;

/**
 * Factory for generating Strategy objects for Phases and steps.
 */
public class StrategyFactory {
    public static Strategy<Phase> generateForPhase(String strategyType) {
        Strategy<Phase> strategy = null;
        if ("serial".equals(strategyType)) {
            strategy = new SerialStrategy.Generator<Phase>().generate();
        } else if ("parallel".equals(strategyType)) {
            strategy = new ParallelStrategy.Generator<Phase>().generate();
        }

        return strategy;
    }

    public static Strategy<Step> generateForSteps(String strategyType) {
        Strategy<Step> strategy = null;
        if ("serial".equals(strategyType)) {
            strategy = new SerialStrategy.Generator<Step>().generate();
        } else if ("parallel".equals(strategyType)) {
            strategy = new ParallelStrategy.Generator<Step>().generate();
        }

        return strategy;
    }
}
