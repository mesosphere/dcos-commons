package com.mesosphere.sdk.specification;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections.CollectionUtils;

import com.mesosphere.sdk.config.ConfigTargetStore;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.StrategyFactory;
import com.mesosphere.sdk.specification.yaml.RawPhase;
import com.mesosphere.sdk.specification.yaml.RawPlan;
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
    private final StepFactory stepFactory;

    public DefaultPlanGenerator(ConfigTargetStore configTargetStore, StateStore stateStore) {
        this(new DefaultStepFactory(configTargetStore, stateStore));
    }

    public DefaultPlanGenerator(StepFactory stepFactory) {
        this.stepFactory = stepFactory;
    }

    @Override
    public Plan generate(RawPlan rawPlan, String planName, Collection<PodSpec> podsSpecs) {
        final List<Phase> phases = rawPlan.getPhases().entrySet().stream()
                .map(entry-> from(entry.getValue(), entry.getKey(), podsSpecs))
                .collect(Collectors.toList());
        return DefaultPlanFactory.getPlan(planName, phases,
                StrategyFactory.generateForPhase(rawPlan.getStrategy()));
    }

    @VisibleForTesting
    protected Phase from(RawPhase rawPhase, String phaseName, Collection<PodSpec> podSpecs) {
        Optional<PodSpec> podSpecOptional = filter(rawPhase.getPod(), podSpecs);
        if (!podSpecOptional.isPresent()) {
            throw new IllegalStateException(String.format(
                    "Unable to find pod '%s' referenced by phase '%s'",
                    rawPhase.getPod(), phaseName));
        }
        PodSpec podSpec = podSpecOptional.get();

        final List<Step> steps = new LinkedList<>();
        if (rawPhase.getSteps() == null || rawPhase.getSteps().isEmpty()) {
            // Generate steps from pod's tasks that are in RUNNING state.
            for (int i = 0; i < podSpec.getCount(); i++) {
                // If the tasks to be launched have been explicitly indicated in the plan
                // override the taskNames.
                if (!CollectionUtils.isEmpty(rawPhase.getTasks())) {
                    for (List<String> taskNames : rawPhase.getTasks()) {
                        steps.add(from(new DefaultPodInstance(podSpec, i), taskNames));
                    }
                } else {
                    List<String> taskNames = podSpec.getTasks().stream()
                            .map(taskSpec -> taskSpec.getName())
                            .collect(Collectors.toList());
                    steps.add(from(new DefaultPodInstance(podSpec, i), taskNames));
                }
            }
        } else {
            for (Map<Integer, List<String>> rawStepMap : rawPhase.getSteps()) {
                if (rawStepMap.size() != 1) {
                    throw new IllegalStateException(String.format(
                            "Malformed step in phase '%s': Map should contain a single entry, but has %d: %s",
                            phaseName, rawStepMap.size(), rawStepMap));
                }
                Map.Entry<Integer, List<String>> rawStep = rawStepMap.entrySet().iterator().next();
                steps.add(from(
                        new DefaultPodInstance(podSpec, rawStep.getKey()),
                        rawStep.getValue()));
            }
        }
        return DefaultPhaseFactory.getPhase(phaseName, steps, StrategyFactory.generateForSteps(rawPhase.getStrategy()));
    }

    private Step from(PodInstance podInstance, List<String> tasksToLaunch) {
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
