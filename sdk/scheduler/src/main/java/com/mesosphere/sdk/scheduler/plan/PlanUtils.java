package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            PodInstance dirytPodInstance = dirtyAsset.getPodInstance();
            Set<String> dirtyTaskNames = new HashSet<>(dirtyAsset.getTasksToLaunch());

            PodInstance assetPodInstance = asset.getPodInstance();
            Set<String> assetTaskNames = new HashSet<>(asset.getTasksToLaunch());

            if (dirytPodInstance.equals(assetPodInstance)) {
                return assetTaskNames.removeAll(dirtyTaskNames);
            }
        }

        return false;
    }
}
