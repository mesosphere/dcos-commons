package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UninstallRecommendation;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.Collection;
import java.util.Optional;

/**
 * Step which implements the uninstalling of a particular reserved resource. For instance, persistent volumes and cpu.
 */
public class ResourceCleanupStep extends UninstallStep {

    private final String resourceId;

    /**
     * Creates a new instance with the provided {@code resourceId} and initial {@code status}.
     */
    public ResourceCleanupStep(String resourceId, Status status) {
        // Avoid having the step name be a pure UUID. Otherwise PlansResource will confuse this UUID with the step UUID:
        super("unreserve-" + resourceId, status);
        this.resourceId = resourceId;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        if (isPending()) {
            logger.info("Setting state to Prepared for resource {}", resourceId);
            setStatus(Status.PREPARED);
        }

        return getPodInstanceRequirement();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
        // Expecting a singleton UninstallRecommendation attached to a resource with a resource ID. If it matches
        // the resource ID for this step, we mark the step as COMPLETE.
        boolean isMatched = recommendations.stream()
                .filter(offerRecommendation -> offerRecommendation instanceof UninstallRecommendation)
                .map(offerRecommendation -> (UninstallRecommendation) offerRecommendation)
                .map(UninstallRecommendation::getResource)
                .map(ResourceUtils::getResourceId)
                .filter(uninstallResourceId -> uninstallResourceId.isPresent())
                .anyMatch(uninstallResourceId -> resourceId.equals(uninstallResourceId.get()));
        if (isMatched) {
            logger.info("Completed dereservation of resource {}", resourceId);
            setStatus(Status.COMPLETE);
        }
    }
}
