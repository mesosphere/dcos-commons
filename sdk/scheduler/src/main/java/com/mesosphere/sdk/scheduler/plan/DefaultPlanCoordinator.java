package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.metrics.PlanReporter;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;

import com.mesosphere.sdk.offer.LoggingUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link PlanCoordinator}.
 */
public class DefaultPlanCoordinator implements PlanCoordinator {

    private final Logger logger;
    private final List<PlanManager> planManagers = new LinkedList<>();
    private final PlanReporter planReporter;

    public DefaultPlanCoordinator(Optional<String> namespace, Collection<PlanManager> planManagers) {
        this.logger = LoggingUtils.getLogger(getClass(), namespace);
        if (CollectionUtils.isEmpty(planManagers)) {
            throw new IllegalArgumentException("At least one plan manager is required");
        }
        this.planManagers.addAll(planManagers);
        this.planReporter = new PlanReporter(namespace, this.planManagers);
    }

    /**
     * Returns the set of steps across all {@link PlanManager}s which are eligible for execution.  Execution normally
     * means that these steps are ready to be matched with offers and launch tasks.
     */
    @Override
    public List<Step> getCandidates() {
        // Assets that are being actively worked on
        final Set<PodInstanceRequirement> dirtiedAssets = new HashSet<>();

        // Pro-actively determine all known dirty assets. This is used to ensure that PlanManagers that are presented
        // with offers first, does not accidentally schedule an asset that's actively being worked upon by another
        // PlanManager that is presented offers later.
        dirtiedAssets.addAll(planManagers.stream()
                .filter(planManager -> !planManager.getPlan().isInterrupted())
                .flatMap(planManager -> planManager.getDirtyAssets().stream())
                .collect(Collectors.toList()));

        List<Step> candidates = new LinkedList<>();
        for (final PlanManager planManager : getPlanManagers()) {
            if (planManager.getPlan().isInterrupted()) {
                logger.info("Skipping interrupted plan: {}", planManager.getPlan().getName());
                continue;
            }

            try {
                Collection<PodInstanceRequirement> relevantDirtyAssets =
                        getRelevantDirtyAssets(planManager, dirtiedAssets);

                // Get candidate steps to be scheduled
                Collection<? extends Step> steps = planManager.getCandidates(relevantDirtyAssets);
                if (!steps.isEmpty()) {
                    logger.info("Got candidate{} {} from {} plan against dirtied assets: all:{}, relevant:{}",
                            steps.size() == 1 ? "" : "s",
                            steps.stream().map(step -> step.getName()).collect(Collectors.toList()),
                            planManager.getPlan().getName(),
                            dirtiedAssets,
                            relevantDirtyAssets);
                }
                candidates.addAll(steps);

                // Collect dirtied assets
                dirtiedAssets.addAll(
                        steps.stream()
                        .filter(step -> step.getPodInstanceRequirement().isPresent())
                        .map(step -> step.getPodInstanceRequirement().get())
                        .collect(Collectors.toList()));
            } catch (Throwable t) {
                logger.error(String.format("Error with %s plan manager", planManager.getPlan().getName()), t);
            }
        }

        if (!candidates.isEmpty()) {
            logger.info("Got total candidates: {}",
                    candidates.stream().map(step -> step.getName()).collect(Collectors.toList()));
        }
        return candidates;
    }

    @Override
    public Collection<PlanManager> getPlanManagers() {
        return planManagers;
    }

    private static Collection<PodInstanceRequirement> getRelevantDirtyAssets(
            PlanManager planManager, Set<PodInstanceRequirement> dirtyAssets) {
        Plan plan = planManager.getPlan();
        return dirtyAssets.stream()
                .filter(podInstanceRequirement -> assetIsRelevant(podInstanceRequirement, plan))
                .collect(Collectors.toList());
    }

    private static boolean assetIsRelevant(PodInstanceRequirement podInstanceRequirement, Plan plan) {
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.isRunning() && step.getPodInstanceRequirement().isPresent())
                .map(step -> step.getPodInstanceRequirement().get())
                .filter(podRequirement -> podRequirement.conflictsWith(podInstanceRequirement))
                .count() == 0;
    }
}
