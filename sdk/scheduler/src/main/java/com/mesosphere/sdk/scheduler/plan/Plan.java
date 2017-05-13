package com.mesosphere.sdk.scheduler.plan;

import java.util.Collection;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.DEPLOY_PLAN_NAME;

/**
 * Defines the interface for a collection of one or more {@link Phase}s, along with any errors encountered while
 * processing those {@link Phase}s. The Plan is a representation of any work that is currently being
 * performed. A Plan is divided into one or more {@link Phase}s, each of which is divided
 * into one or more {@link Step}s. This structure is a logical abstraction of a multi-phase process
 * for performing upgrades or maintenance on a service.
 * <p>
 * To give a more concrete example, imagine a database which contains both "Data" nodes and "Index"
 * nodes, and which must be upgraded by first rolling out new Data nodes followed by new Index
 * nodes. This upgrade process could be represented as a single Plan with two Phases, where Phase-0
 * is upgrading the Data nodes and Phase-1 is upgrading the Index nodes. Each Phase would then
 * contain a list of {@link Step}s which each reference an individual Data or Index node to be upgraded.
 * If any errors occurred during the rollout, the process would pause and the Plan would contain a
 * list of one or more error messages to be shown to the user.
 */
public interface Plan extends ParentElement<Phase> {
    default Collection<? extends Step> getCandidates(Collection<PodInstanceRequirement> dirtyAssets) {
        Collection<Phase> candidatePhases = getStrategy().getCandidates(getChildren(), dirtyAssets);
        Collection<Step> candidateSteps = candidatePhases.stream()
                .map(phase -> phase.getStrategy().getCandidates(phase.getChildren(), dirtyAssets))
                .flatMap(steps -> steps.stream())
                .collect(Collectors.toList());

        return candidateSteps;
    }

    default boolean isDeployPlan() {
        return getName().equals(DEPLOY_PLAN_NAME);
    }
}
