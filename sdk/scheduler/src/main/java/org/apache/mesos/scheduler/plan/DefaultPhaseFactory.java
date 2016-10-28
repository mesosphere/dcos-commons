package org.apache.mesos.scheduler.plan;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.PodSet;

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
    public Phase getPhase(PodSet podSet) {
        return getPhase(podSet, strategyGenerator.generate());
    }

    @Override
    public Phase getPhase(PodSet podSet, Strategy<Step> strategy) {
        return new DefaultPhase(
                podSet.getName(),
                getSteps(podSet),
                strategy,
                Collections.emptyList());
    }

    @Override
    public List<Phase> getPhases(List<PodSet> podSets, StrategyGenerator<Step> strategyGenerator) {
        return podSets.stream()
                .map(podSet -> getPhase(podSet, strategyGenerator.generate()))
                .collect(Collectors.toList());
    }

    private List<Step> getSteps(PodSet podSet) {
        return podSet.getPods().stream()
                .map(pod -> {
                    try {
                        return stepFactory.getStep(pod);
                    } catch (Step.InvalidStepException | InvalidProtocolBufferException e) {
                        return new DefaultStep(
                                pod.getName(),
                                Optional.empty(),
                                Status.ERROR,
                                Arrays.asList(ExceptionUtils.getStackTrace(e)));
                    }
                })
                .collect(Collectors.toList());
    }
}
