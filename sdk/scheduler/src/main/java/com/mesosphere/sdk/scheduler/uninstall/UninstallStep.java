package com.mesosphere.sdk.scheduler.uninstall;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceCollectUtils;
import com.mesosphere.sdk.offer.UninstallRecommendation;
import com.mesosphere.sdk.scheduler.plan.AbstractStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;

import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Step which implements the uninstalling of a particular persistent volume or resource.
 */
public class UninstallStep extends AbstractStep {

    /**
     * Creates a new instance with the provided {@code resourceId} and initial {@code status}.
     */
    public UninstallStep(String resourceId, Status status) {
        super(resourceId, status);
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        if (getStatus().equals(Status.PENDING)) {
            logger.info("Setting state to Prepared for resource {}", getName());
            setStatus(Status.PREPARED);
        }

        return getPodInstanceRequirement();
    }

    @Override
    public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
        // Expecting a singleton UninstallRecommendation attached to a resource with a resource ID. If it matches
        // the resource ID for this step, we mark the step as COMPLETE.
        boolean isMatched = recommendations.stream()
                .filter(offerRecommendation -> offerRecommendation instanceof UninstallRecommendation)
                .map(offerRecommendation -> (UninstallRecommendation) offerRecommendation)
                .map(UninstallRecommendation::getResource)
                .map(ResourceCollectUtils::getResourceId)
                .filter(uninstallResourceId -> uninstallResourceId.isPresent())
                .anyMatch(uninstallResourceId -> getName().equals(uninstallResourceId.get()));
        if (isMatched) {
            logger.info("Completed uninstall step for resource {}", getName());
            setStatus(Status.COMPLETE);
        }
    }

    @Override
    public Optional<PodInstanceRequirement> getAsset() {
        return getPodInstanceRequirement();
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public void update(Protos.TaskStatus status) {
        logger.debug("Step {} ignoring irrelevant TaskStatus: {}", getName(), TextFormat.shortDebugString(status));
    }
}
