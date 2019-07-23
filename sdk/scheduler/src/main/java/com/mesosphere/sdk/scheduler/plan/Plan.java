package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.Constants;

import java.util.Collection;
import java.util.stream.Collectors;

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
    return getStrategy()
        .getCandidates(getChildren(), dirtyAssets)
        .stream()
        .map(phase -> phase.getStrategy().getCandidates(phase.getChildren(), dirtyAssets))
        .flatMap(Collection::stream)
        .filter(step -> !step.isDelayed())
        .collect(Collectors.toList());
  }

  default boolean isDeployPlan() {
    return getName().equals(Constants.DEPLOY_PLAN_NAME);
  }

  default boolean isRecoveryPlan() {
    return getName().equals(Constants.RECOVERY_PLAN_NAME);
  }

  default boolean isDecommissionPlan() {
    return getName().equals(Constants.DECOMMISSION_PLAN_NAME);
  }
}
