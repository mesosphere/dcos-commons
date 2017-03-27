package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.List;

/**
 * Factory for generating Strategy objects for Phases and steps.
 */
public class StrategyFactory {
    public static Strategy<Phase> generateForPhase(String strategyType) {
        if (strategyType == null) {
            return new SerialStrategy.Generator<Phase>().generate();
        }
        Strategy<Phase> strategy = null;
        switch (strategyType) {
            case "parallel":
                strategy = new ParallelStrategy.Generator<Phase>().generate();
                break;
            case "serial":
                // fall through
            default:
                strategy = new SerialStrategy.Generator<Phase>().generate();
        }

        return strategy;
    }

    public static Strategy<Step> generateForSteps(String strategyType, List<Step> steps) {
        if (strategyType == null) {
            return new SerialStrategy.Generator<Step>().generate();
        }
        Strategy<Step> strategy = null;
        switch (strategyType) {
            case "parallel":
                strategy = new ParallelStrategy.Generator<Step>().generate();
                break;
            case "parallel-canary":
                strategy = new CanaryStrategy.Generator(new ParallelStrategy<>(), steps).generate();
                break;
            case "canary":
                // fall through: default to serial behavior following canary stage
            case "serial-canary":
                strategy = new CanaryStrategy.Generator(new SerialStrategy<>(), steps).generate();
                break;
            case "serial":
                // fall through
            default:
                strategy = new SerialStrategy.Generator<Step>().generate();
        }

        return strategy;
    }
}
