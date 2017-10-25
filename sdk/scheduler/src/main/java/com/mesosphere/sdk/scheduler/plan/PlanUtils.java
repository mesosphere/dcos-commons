package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.TaskUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Common utility methods for {@link PlanManager}s.
 */
public class PlanUtils {

    private PlanUtils() {
        // do not instantiate
    }

    public static boolean allHaveStatus(Status status, Collection<? extends Element> elements) {
        return elements.stream().allMatch(element -> element.getStatus() == status);
    }

    public static boolean anyHaveStatus(Status status, Collection<? extends Element> elements) {
        return elements.stream().anyMatch(element -> element.getStatus() == status);
    }

    public static List<Offer> filterAcceptedOffers(List<Offer> offers, Collection<OfferID> acceptedOfferIds) {
        return offers.stream().filter(offer -> !acceptedOfferIds.contains(offer.getId())).collect(Collectors.toList());
    }

    /**
     * Indicates whether a plan has any work left to do.  A plan can be in ERROR state because of a rejected
     * target configuration, but still have work to do reaching it's target configuration.  If all of a plan's
     * elements are not complete, it has operations.
     */
    public static boolean hasOperations(Plan plan) {
        boolean complete = allHaveStatus(Status.COMPLETE, plan.getChildren());
        boolean interrupted = plan.isInterrupted();
        return !complete && !interrupted;
    }

    /**
     * Determines whether the specified asset refers to the same pod instance and tasks other assets.
     * @param asset The asset of interest.
     * @param dirtyAssets Other assets which may conflict with the {@code asset}
     */
    public static boolean assetConflicts(PodInstanceRequirement asset, Collection<PodInstanceRequirement> dirtyAssets) {
        return dirtyAssets.stream()
                .filter(dirtyAsset -> asset.conflictsWith(dirtyAsset))
                .count() > 0;
    }

    public static List<PlanManager> getActivePlanManagers(List<PlanManager> planManagers) {
        return planManagers.stream()
                .filter(planManager -> !planManager.getPlan().isInterrupted())
                .collect(Collectors.toList());
    }

    public static Set<String> getLaunchableTasks(Collection<Plan> plans) {
        return plans.stream()
                .flatMap(plan -> plan.getChildren().stream())
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.getPodInstanceRequirement().isPresent())
                .map(step -> step.getPodInstanceRequirement().get())
                .flatMap(podInstanceRequirement ->
                        TaskUtils.getTaskNames(
                                podInstanceRequirement.getPodInstance(),
                                podInstanceRequirement.getTasksToLaunch()).stream())
                .collect(Collectors.toSet());
    }

    public static Set<PodInstanceRequirement> getDirtyAssets(Plan plan) {
        if (plan == null) {
            return Collections.emptySet();
        }

        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.isAssetDirty() && step.getPodInstanceRequirement().isPresent())
                .map(step -> step.getPodInstanceRequirement().get())
                .collect(Collectors.toSet());
    }
}
