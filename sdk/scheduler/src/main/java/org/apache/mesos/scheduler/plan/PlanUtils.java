package org.apache.mesos.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commons utility methods for {@code PlanManager}s.
 */
public class PlanUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanUtils.class);

    public static final Collection<? extends Step> getCandidates(Plan plan, Collection<String> dirtyAssets) {
        Collection<Phase> candidatePhases = plan.getStrategy().getCandidates(plan, dirtyAssets);
        Collection<Step> candidateSteps = candidatePhases.stream()
                .map(phase -> phase.getStrategy().getCandidates(phase, dirtyAssets))
                .flatMap(steps -> steps.stream())
                .collect(Collectors.toList());

        return candidateSteps;
    }

    @SuppressWarnings("unchecked")
    public static final void update(Element parent, TaskStatus taskStatus) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Updated {} with TaskStatus: {}", parent.getName(), taskStatus);
        children.forEach(element -> element.update(taskStatus));
    }

    @SuppressWarnings("unchecked")
    public static final void restart(Element parent) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Restarting elements within {}: {}", parent.getName(), children);
        children.forEach(element -> element.restart());
    }

    @SuppressWarnings("unchecked")
    public static final void forceComplete(Element parent) {
        Collection<? extends Element> children = parent.getChildren();
        LOGGER.info("Forcing completion of elements within {}: {}", parent.getName(), children);
        children.forEach(element -> element.forceComplete());
    }

    public static final String getMessage(Element element) {
        return String.format("%s: '%s [%s]' has status: '%s'.",
                element.getClass().getName(), element.getName(), element.getId(), element.getStatus());
    }

    @SuppressWarnings("unchecked")
    public static final List<String> getErrors(List<String> errors, Element parent) {
        Collection<? extends Element> children = parent.getChildren();
        children.forEach(element -> errors.addAll(element.getErrors()));
        return errors;
    }

    @SuppressWarnings("unchecked")
    public static final Status getStatus(Element parent) {
        // Ordering matters throughout this method.  Modify with care.

        Collection<? extends Element> children = parent.getChildren();
        if (children == null) {
            LOGGER.error("Cannot determine status of null elements in {}.", parent.getName());
            return Status.ERROR;
        }

        Status result;
        if (anyHaveStatus(Status.ERROR, children)) {
            result = Status.ERROR;
            LOGGER.warn("({} status={}) Elements contains errors", parent.getName(), result);
        } else if (CollectionUtils.isEmpty(children)) {
            result = Status.COMPLETE;
            LOGGER.warn("({} status={}) Empty collection of elements encountered.", parent.getName(), result);
        } else if (anyHaveStatus(Status.IN_PROGRESS, children)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("({} status={}) At least one phase has status: {}",
                    parent.getName(), result, Status.IN_PROGRESS);
        } else if (anyHaveStatus(Status.WAITING, children)) {
            result = Status.WAITING;
            LOGGER.info("({} status={}) At least one element has status: {}",
                    parent.getName(), result, Status.WAITING);
        } else if (allHaveStatus(Status.COMPLETE, children)) {
            result = Status.COMPLETE;
            LOGGER.info("({} status={}) All elements have status: {}",
                    parent.getName(), result, Status.COMPLETE);
        } else if (allHaveStatus(Status.PENDING, children)) {
            result = Status.PENDING;
            LOGGER.info("({} status={}) All elements have status: {}",
                    parent.getName(), result, Status.PENDING);
        } else if (anyHaveStatus(Status.COMPLETE, children)
                && anyHaveStatus(Status.PENDING, children)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("({} status={}) At least one element has status '{}' and one has status '{}'",
                    parent.getName(), result, Status.COMPLETE, Status.PENDING);
        } else {
            result = Status.ERROR;
            LOGGER.error("({} status={}) Unexpected state. PlanElements: {}",
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

    public static void setStatus(List<? extends Element> elements, Status status) {
        elements.forEach(element -> element.setStatus(status));
    }
}
