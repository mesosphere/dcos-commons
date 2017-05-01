package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.PodInstance;
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

    public static boolean assetConflicts(PodInstanceRequirement asset, Collection<PodInstanceRequirement> dirtyAssets) {
        for (PodInstanceRequirement dirtyAsset : dirtyAssets) {
            PodInstance dirtyPodInstance = dirtyAsset.getPodInstance();
            Set<String> dirtyTaskNames = new HashSet<>(dirtyAsset.getTasksToLaunch());

            PodInstance assetPodInstance = asset.getPodInstance();
            Set<String> assetTaskNames = new HashSet<>(asset.getTasksToLaunch());

            if (podInstancesConflict(dirtyPodInstance, assetPodInstance)) {
                return true;
            }
        }

        return false;
    }

    public static boolean podInstancesConflict(PodInstance podInstance0, PodInstance podInstance1) {
        boolean sameType = podInstance0.getPod().getType().equals(podInstance1.getPod().getType());
        boolean sameIndex = podInstance0.getIndex() == podInstance1.getIndex();
        return sameType && sameIndex;
    }

    public static Collection<PodInstanceRequirement> getRelevantDirtyAssets(
            PlanManager planManager,
            Set<PodInstanceRequirement> dirtyAssets) {
        Collection<PodInstanceRequirement> relevantDirtyAssets = new ArrayList<>();
        for (PodInstanceRequirement dirtyAsset : dirtyAssets) {
            for (PodInstanceRequirement localDirtyAsset : planManager.getDirtyAssets()) {
                if (!PlanUtils.podInstancesConflict(dirtyAsset.getPodInstance(), localDirtyAsset.getPodInstance())) {
                    relevantDirtyAssets.add(dirtyAsset);
                }
            }
        }

        return relevantDirtyAssets;
    }

    public static List<PlanManager> getActivePlanManagers(List<PlanManager> planManagers) {
        return planManagers.stream()
                .filter(planManager -> !planManager.getPlan().isInterrupted())
                .collect(Collectors.toList());
    }
}
