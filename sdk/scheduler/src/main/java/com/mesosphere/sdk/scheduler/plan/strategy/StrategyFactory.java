package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

/**
 * Factory for generating Strategy objects for Phases and steps.
 */
public class StrategyFactory {

    private List<Strategy> strategies;

    public StrategyFactory() {
      strategies = new ArrayList<Strategy>(Arrays.asList(
        new SerialStrategy(),
        new ParallelStrategy()));
    }

    public void addCustomStrategy(Strategy strategy) {
      strategies.add(strategy);
    }

    public Strategy<Phase> generateForPhase(String strategyType) {
        if (strategyType == null) {
            return new SerialStrategy.Generator<Phase>().generate();
        }
        for (int it = 0; it < strategies.size(); it++){
          Strategy s = strategies.get(it);
          if (strategyType.equals(s.getName())) {
            return s.getGenerator().generate();
          }
        }
        return new SerialStrategy.Generator<Phase>().generate();
    }

    public Strategy<Step> generateForSteps(String strategyType, List<Step> steps) {
        if (strategyType == null) {
            return new SerialStrategy.Generator<Step>().generate();
        }

        for (int it = 0; it < strategies.size(); it++){
          Strategy s = strategies.get(it);
          if (strategyType.equals(s.getName())) {
            return s.getGenerator().generate();
          } else {
            switch (strategyType) {
                case "parallel-canary":
                    return new CanaryStrategy.Generator(new ParallelStrategy<>(), steps).generate();
                case "canary":
                    return new SerialStrategy.Generator<Step>().generate();
                case "serial-canary":
                    return new CanaryStrategy.Generator(new SerialStrategy<>(), steps).generate();
            }
          }
        }
        return new SerialStrategy.Generator<Step>().generate();
    }
}
