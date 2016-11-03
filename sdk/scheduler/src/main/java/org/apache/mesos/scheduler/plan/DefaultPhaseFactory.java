package org.apache.mesos.scheduler.plan;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyGenerator;
import org.apache.mesos.specification.PodSetSpecification;

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
    public Phase getPhase(PodSetSpecification podSetSpecification) {
        return getPhase(podSetSpecification, strategyGenerator.generate());
    }

    @Override
    public Phase getPhase(PodSetSpecification podSetSpecification, Strategy<Step> strategy) {
        return new DefaultPhase(
                podSetSpecification.getName(),
                getSteps(podSetSpecification),
                strategy,
                Collections.emptyList());
    }

    @Override
    public List<Phase> getPhases(List<PodSetSpecification> podSetSpecifications, StrategyGenerator<Step> strategyGenerator) {
        return podSetSpecifications.stream()
                .map(podSet -> getPhase(podSet, strategyGenerator.generate()))
                .collect(Collectors.toList());
    }

    private List<Step> getSteps(PodSetSpecification podSetSpecification) {
        return podSetSpecification.getPodSpecifications().stream()
                .map(pod -> {
                    try {
                        return stepFactory.getStep(pod);
                    } catch (Step.InvalidStepException |
                            InvalidProtocolBufferException |
                            InvalidRequirementException e) {
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
