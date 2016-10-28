package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.TaskSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class generates Phases given PhaseSpecifications.
 */
public class DefaultPhaseFactory implements PhaseFactory {

    private final StepFactory stepFactory;
    private final StrategyGenerator<Step> strategyGenerator;

    public DefaultPhaseFactory(StepFactory stepFactory) {
        this(stepFactory, new SerialStrategy.Generator<>());
    }

    public DefaultPhaseFactory(StepFactory stepFactory, StrategyGenerator<Step> strategyGenerator) {
        this.stepFactory = stepFactory;
        this.strategyGenerator = strategyGenerator;
    }

    public static Phase getPhase(String name, List<Step> steps, Strategy<Step> strategy) {
        return new DefaultPhase(
                name,
                steps,
                strategy,
                Collections.emptyList());
    }

    @Override
    public Phase getPhase(TaskSet taskSet) {
        return getPhase(taskSet, strategyGenerator.generate());
    }

    @Override
    public Phase getPhase(TaskSet taskSet, Strategy<Step> strategy) {
        return new DefaultPhase(
                taskSet.getName(),
                getSteps(taskSet),
                strategy,
                Collections.emptyList());
    }

    @Override
    public List<Phase> getPhases(List<TaskSet> taskSets, StrategyGenerator<Step> strategyGenerator) {
        return taskSets.stream()
                .map(taskSet -> getPhase(taskSet, strategyGenerator.generate()))
                .collect(Collectors.toList());
    }

    private List<Step> getSteps(TaskSet taskSet) {
        return taskSet.getTaskSpecifications().stream()
                .map(taskSpec -> {
                    try {
                        return stepFactory.getStep(taskSpec);
                    } catch (Step.InvalidStepException e) {
                        return new DefaultStep(
                                taskSpec.getName(),
                                Optional.empty(),
                                Status.ERROR,
                                Arrays.asList(ExceptionUtils.getStackTrace(e)));
                    }
                })
                .collect(Collectors.toList());
    }
}
