package com.mesosphere.sdk.scheduler.plan;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
        Collection<Phase> candidatePhases = plan.getStrategy().getCandidates(plan, dirtyAssets);
        Collection<Step> candidateSteps = candidatePhases.stream()
                .map(phase -> phase.getStrategy().getCandidates(phase, dirtyAssets))
                .flatMap(steps -> steps.stream())
                .collect(Collectors.toList());

        return candidateSteps;
    }

    public static Set<String> getDirtyAssets(Plan plan) {
        if (plan == null) {
            return Collections.emptySet();
        }
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.isInProgress())
                .map(step -> step.getName())
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("rawtypes")
    public static final void update(Element<? extends Element> parent, TaskStatus taskStatus) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Updated {} with TaskStatus: {}", parent.getName(), TextFormat.shortDebugString(taskStatus));
        children.forEach(element -> element.update(taskStatus));
    }

    @SuppressWarnings("rawtypes")
    public static final void restart(Element<? extends Element> parent) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Restarting elements within {}: {}", parent.getName(), children);
        children.forEach(element -> element.restart());
    }

    @SuppressWarnings("rawtypes")
    public static final void forceComplete(Element<? extends Element> parent) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Forcing completion of elements within {}: {}", parent.getName(), children);
        children.forEach(element -> element.forceComplete());
    }

    /**
     * Returns a reasonable user-visible status message describing this {@link Element}.
     */
    public static final String getMessage(Element<?> element) {
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
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static final List<String> getErrors(List<String> parentErrors, Element<? extends Element> parent) {
        // Note that this function MUST NOT call parent.getErrors() as that creates a circular call.
        List<String> errors = new ArrayList<>(); // copy list to avoid modifying parentErrors in-place
        errors.addAll(parentErrors);
        Collection<? extends Element> children = parent.getChildren();
        children.forEach(element -> errors.addAll(element.getErrors()));
        return errors;
    }

    @SuppressWarnings("rawtypes")
    public static final Status getStatus(Element<? extends Element> parent) {
        // Ordering matters throughout this method.  Modify with care.
        // Also note that this function MUST NOT call parent.getStatus() as that creates a circular call.

        final Strategy<? extends Element> strategy = parent.getStrategy();
        if (strategy == null) {
            LOGGER.error("Parent element returned null strategy: {}", parent.getName());
            return Status.ERROR;
        }

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
        } else if (anyHaveStatus(Status.PREPARED, children)) {
            result = Status.PREPARED;
            LOGGER.debug("({} status={}) At least one phase has status: {}",
                    parent.getName(), result, Status.PREPARED);
        } else if (anyHaveStatus(Status.WAITING, children)) {
            result = Status.WAITING;
            LOGGER.debug("({} status={}) At least one element has status: {}",
                    parent.getName(), result, Status.WAITING);
        } else if (allHaveStatus(Status.COMPLETE, children)) {
            result = Status.COMPLETE;
            LOGGER.debug("({} status={}) All elements have status: {}",
                    parent.getName(), result, Status.COMPLETE);
        } else if (allHaveStatus(Status.PENDING, children)) {
            result = Status.PENDING;
            LOGGER.debug("({} status={}) All elements have status: {}",
                    parent.getName(), result, Status.PENDING);
        } else if (anyHaveStatus(Status.COMPLETE, children)
                && anyHaveStatus(Status.PENDING, children)) {
            result = Status.PREPARED;
            LOGGER.debug("({} status={}) At least one element has status '{}' and one has status '{}'",
                    parent.getName(), result, Status.COMPLETE, Status.PENDING);
        } else if (anyHaveStatus(Status.STARTING, children)) {
            result = Status.STARTING;
            LOGGER.debug("({} status={}) At least one element has status '{}'",
                    parent.getName(), result, Status.STARTING);
        } else {
            result = Status.ERROR;
            LOGGER.debug("({} status={}) Unexpected state. PlanElements: {}",
                    parent.getName(), result, children);
        }

        return result;
    }

    @SuppressWarnings("rawtypes")
    public static boolean allHaveStatus(Status status, Collection<? extends Element> elements) {
        return elements.stream().allMatch(element -> element.getStatus() == status);
    }

    @SuppressWarnings("rawtypes")
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
