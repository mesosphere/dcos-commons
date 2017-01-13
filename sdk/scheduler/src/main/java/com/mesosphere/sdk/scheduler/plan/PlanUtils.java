package com.mesosphere.sdk.scheduler.plan;

import com.google.protobuf.TextFormat;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.Constants.DEPLOY_PLAN_NAME;

/**
 * Common utility methods for {@link PlanManager}s.
 */
public class PlanUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanUtils.class);

    private PlanUtils() {
        // do not instantiate
    }

    public static final Collection<? extends Step> getCandidates(Plan plan, Collection<String> dirtyAssets) {
        Collection<Phase> candidatePhases = plan.getStrategy().getCandidates(plan.getChildren(), dirtyAssets);
        Collection<Step> candidateSteps = candidatePhases.stream()
                .map(phase -> phase.getStrategy().getCandidates(phase.getChildren(), dirtyAssets))
                .flatMap(steps -> steps.stream())
                .collect(Collectors.toList());

        return candidateSteps;
    }

    /**
     * Implements default logic for determining whether the provided {@link Element} appears to be eligible for
     * performing work.
     */
    public static boolean isEligibleCandidate(Element element, Collection<String> dirtyAssets) {
        if (element instanceof Interruptible && ((Interruptible) element).isInterrupted()) {
            return false;
        }
        if (element instanceof Step) {
            Optional<String> asset = ((Step) element).getAsset();
            if (asset.isPresent() && dirtyAssets.contains(asset.get())) {
                return false;
            }
        }
        return !element.isComplete() && !element.hasErrors();
    }

    public static final void update(ParentElement<? extends Element> parent, TaskStatus taskStatus) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Updated {} with TaskStatus: {}", parent.getName(), TextFormat.shortDebugString(taskStatus));
        children.forEach(element -> element.update(taskStatus));
    }

    public static final void restart(ParentElement<? extends Element> parent) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Restarting elements within {}: {}", parent.getName(), children);
        children.forEach(element -> element.restart());
    }

    public static final void forceComplete(ParentElement<? extends Element> parent) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Forcing completion of elements within {}: {}", parent.getName(), children);
        children.forEach(element -> element.forceComplete());
    }

    /**
     * Returns a reasonable user-visible status message describing this {@link Element}.
     */
    public static final String getMessage(Element element) {
        return String.format("%s: '%s [%s]' has status: '%s'.",
                element.getClass().getName(), element.getName(), element.getId(), element.getStatus());
    }

    /**
     * Returns all of the errors from the parent and all its child {@link Element}s. Intended for
     * use by implementations of {@Link Element#getErrors()}.
     *
     * @param parentErrors Errors from the parent itself, to be copied into the returned list
     * @param parent The parent element whose children will be scanned
     * @return a combined list of all errors from the parent and all its children
     */
    public static final List<String> getErrors(List<String> parentErrors, ParentElement<? extends Element> parent) {
        // Note that this function MUST NOT call parent.getErrors() as that creates a circular call.
        List<String> errors = new ArrayList<>(); // copy list to avoid modifying parentErrors in-place
        errors.addAll(parentErrors);
        Collection<? extends Element> children = parent.getChildren();
        children.forEach(element -> errors.addAll(element.getErrors()));
        return errors;
    }

    public static final Status getStatus(ParentElement<? extends Element> parent) {
        // Ordering matters throughout this method.  Modify with care.
        // Also note that this function MUST NOT call parent.getStatus() as that creates a circular call.

        final Collection<? extends Element> children = parent.getChildren();
        if (children == null) {
            LOGGER.error("Parent element returned null list of children: {}", parent.getName());
            return Status.ERROR;
        }

        Status result;
        if (!parent.getErrors().isEmpty()) {
            result = Status.ERROR;
            LOGGER.debug("({} status={}) Elements contains errors", parent.getName(), result);
        } else if (CollectionUtils.isEmpty(children)) {
            result = Status.COMPLETE;
            LOGGER.debug("({} status={}) Empty collection of elements encountered.", parent.getName(), result);
        } else if (allHaveStatus(Status.COMPLETE, children)) {
            result = Status.COMPLETE;
            LOGGER.debug("({} status={}) All elements have status: {}",
                    parent.getName(), result, Status.COMPLETE);
        } else if (parent.isInterrupted()) {
            result = Status.WAITING;
            LOGGER.info("({} status={}) Parent element is interrupted", parent.getName(), result);
        } else if (anyHaveStatus(Status.WAITING, children)) {
            result = Status.WAITING;
            LOGGER.debug("({} status={}) At least one element has status: {}",
                    parent.getName(), result, Status.WAITING);
        } else if (allHaveStatus(Status.PENDING, children)) {
            result = Status.PENDING;
            LOGGER.debug("({} status={}) All elements have status: {}",
                    parent.getName(), result, Status.PENDING);
        } else if (anyHaveStatus(Status.PREPARED, children)) {
            result = Status.IN_PROGRESS;
            LOGGER.debug("({} status={}) At least one phase has status: {}",
                    parent.getName(), result, Status.PREPARED);
        } else if (anyHaveStatus(Status.IN_PROGRESS, children)) {
            result = Status.IN_PROGRESS;
            LOGGER.debug("({} status={}) At least one phase has status: {}",
                    parent.getName(), result, Status.IN_PROGRESS);
        } else if (anyHaveStatus(Status.COMPLETE, children) && (anyHaveStatus(Status.PENDING, children))) {
            result = Status.IN_PROGRESS;
            LOGGER.debug("({} status={}) At least one element has status '{}' and one has status '{}'",
                    parent.getName(), result, Status.COMPLETE, Status.PENDING);
        } else if (anyHaveStatus(Status.STARTING, children)) {
            result = Status.STARTING;
            LOGGER.debug("({} status={}) At least one element has status '{}'",
                    parent.getName(), result, Status.STARTING);
        } else {
            result = Status.ERROR;
            LOGGER.warn("({} status={}) Unexpected state. children: {}",
                    parent.getName(), result, children);
        }

        return result;
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

    public static boolean isDeployPlan(Plan plan) {
        return plan.getName().equals(DEPLOY_PLAN_NAME);
    }
}
