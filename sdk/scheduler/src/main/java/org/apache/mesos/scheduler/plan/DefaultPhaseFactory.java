package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.specification.PodSpec;

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

    public DefaultPhaseFactory(StepFactory stepFactory) {
        this.stepFactory = stepFactory;
    }

    public static Phase getPhase(String name, List<Step> steps, Strategy<Step> strategy) {
        return new DefaultPhase(
                name,
                steps,
                strategy,
                Collections.emptyList());
    }

    @Override
    public Phase getPhase(List<PodSpec> podSpecs, Strategy<Step> strategy) {
        return new DefaultPhase(
                getName(podSpecs),
                getSteps(podSpecs),
                strategy,
                Collections.emptyList());
    }

    private List<Step> getSteps(List<PodSpec> podSpecs) {
        return podSpecs.stream()
                .map(podSpec -> {
                    try {
                        return stepFactory.getStep(podSpec);
                    } catch (Step.InvalidStepException | InvalidRequirementException e) {
                        return new DefaultStep(
                                podSpec.getName(),
                                Optional.empty(),
                                Status.ERROR,
                                Arrays.asList(ExceptionUtils.getStackTrace(e)));
                    }
                })
                .collect(Collectors.toList());
    }

    private String getName(List<PodSpec> podSpecs) {
        String name = "default-phase-name";

        Optional<PodSpec> podSpecOptional  = podSpecs.stream().findFirst();
        if (podSpecOptional.isPresent()) {
            name = podSpecOptional.get().getType();
        }

        return name;
    }
}
