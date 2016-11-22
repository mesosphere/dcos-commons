package com.mesosphere.sdk.specification;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections.CollectionUtils;
import com.mesosphere.sdk.config.ConfigTargetStore;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.StrategyFactory;
import com.mesosphere.sdk.specification.yaml.RawPhase;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawStep;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link PlanGenerator}.
 */
public class DefaultPlanGenerator implements PlanGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanGenerator.class);
    private final DefaultStepFactory stepFactory;

    public DefaultPlanGenerator(
            ConfigTargetStore configTargetStore,
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider) {
        this.stepFactory = new DefaultStepFactory(configTargetStore,
                stateStore, offerRequirementProvider);
    }

    @Override
    public Plan generate(RawPlan rawPlan, Collection<PodSpec> podsSpecs) {
        for (Map.Entry<String, RawPhase> entry : rawPlan.getPhases().entrySet()) {
            entry.getValue().setName(entry.getKey());
        }
        final List<Phase> phases = rawPlan.getPhases().values().stream()
                .map(rawPhase -> from(rawPhase, podsSpecs))
                .collect(Collectors.toList());
        return DefaultPlanFactory.getPlan(rawPlan.getName(), phases,
                StrategyFactory.generateForPhase(rawPlan.getStrategy()));
    }

    @VisibleForTesting
    protected Phase from(RawPhase rawPhase, Collection<PodSpec> podsSpecs) {
        String name = rawPhase.getName();
        String pod = rawPhase.getPod();
        List<RawStep> rawSteps = rawPhase.getSteps();
        String strategy = rawPhase.getStrategy();

        Optional<PodSpec> podSpecOptnl = filter(pod, podsSpecs);
        if (!podSpecOptnl.isPresent()) {
            throw new IllegalStateException("Pod not found: " + pod);
        }

        PodSpec podSpec = podSpecOptnl.get();
        Integer count = podSpec.getCount();

        Phase phase;
        final List<Step> steps = new LinkedList<>();
        if (CollectionUtils.isEmpty(rawSteps)) {
            // Generate steps from pod's tasks that are in RUNNING state.
            for (int i = 0; i < count; i++) {
                DefaultPodInstance podInstance = new DefaultPodInstance(podSpec, i);
                final List<TaskSpec> taskSpecs = podSpec.getTasks();
                List<String> taskNames = taskSpecs.stream()
                        .map(taskSpec -> taskSpec.getName())
                        .collect(Collectors.toList());
                for (TaskSpec taskSpec : taskSpecs) {
                    if (taskSpec.getGoal() == GoalState.RUNNING) {
                        steps.add(from(podInstance, taskNames));
                    }
                }
            }
        } else {
            boolean allHaveIds = rawSteps.stream().allMatch(rawStep -> rawStep.getPodInstance().isPresent());
            boolean noneHaveIds = rawSteps.stream().allMatch(rawStep -> !rawStep.getPodInstance().isPresent());

            if (noneHaveIds) {
                for (int i = 0; i < count; i++) {
                    for (RawStep rawStep : rawSteps) {
                        DefaultPodInstance podInstance = new DefaultPodInstance(podSpec, i);
                        List<String> taskNames = rawStep.getTasks();
                        steps.add(from(podInstance, taskNames));
                    }
                }
            } else if (allHaveIds) {
                for (RawStep rawStep : rawSteps) {
                    DefaultPodInstance podInstance = new DefaultPodInstance(podSpec,
                            rawStep.getPodInstance().get());
                    List<String> taskNames = rawStep.getTasks();
                    steps.add(from(podInstance, taskNames));
                }
            } else {
                throw new IllegalStateException("podInstance should be specified for all steps " +
                        "or should be omitted for all steps.");
            }
        }
        phase = DefaultPhaseFactory.getPhase(name, steps, StrategyFactory.generateForSteps(strategy));
        return phase;
    }

    @VisibleForTesting
    protected Step from(PodInstance podInstance, List<String> tasksToLaunch) {
        try {
            return stepFactory.getStep(podInstance, tasksToLaunch);
        } catch (Exception e) {
            LOGGER.error("Failed to generate step", e);
            throw new IllegalStateException(e);
        }
    }

    @VisibleForTesting
    protected Optional<PodSpec> filter(String podType, Collection<PodSpec> podSpecs) {
        return podSpecs.stream().filter(podSpec -> podSpec.getType().equals(podType)).findFirst();
    }
}
