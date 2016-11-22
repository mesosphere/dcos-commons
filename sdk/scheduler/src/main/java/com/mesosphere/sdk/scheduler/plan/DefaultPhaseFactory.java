package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    public Phase getPhase(PodSpec podSpec, Strategy<Step> strategy) {
        return new DefaultPhase(
                podSpec.getType(),
                getSteps(podSpec),
                strategy,
                Collections.emptyList());
    }

    @Override
    public Phase getPhase(PodSpec podSpec) {
        return getPhase(podSpec, new SerialStrategy<>());
    }

    private List<Step> getSteps(PodSpec podSpec) {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < podSpec.getCount(); i++) {
            PodInstance podInstance = new DefaultPodInstance(podSpec, i);

            List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                    .filter(taskSpec -> taskSpec.getGoal().equals(GoalState.RUNNING))
                    .map(taskSpec -> taskSpec.getName())
                    .collect(Collectors.toList());

            try {
                steps.add(stepFactory.getStep(podInstance, tasksToLaunch));
            } catch (Step.InvalidStepException | InvalidRequirementException e) {
                steps.add(new DefaultStep(
                        PodInstance.getName(podSpec, i),
                        Status.ERROR,
                        podInstance,
                        Collections.emptyList(),
                        Arrays.asList(ExceptionUtils.getStackTrace(e))));
            }
        }

        return steps;
    }
}
