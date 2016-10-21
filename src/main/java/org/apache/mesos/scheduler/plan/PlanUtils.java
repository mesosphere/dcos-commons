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

    public static final Collection<? extends Block> getCandidates(Plan plan, Collection<String> dirtyAssets) {
        Collection<Phase> candidatePhases = plan.getStrategy().getCandidates(plan, dirtyAssets);
        Collection<Block> candidateBlocks = candidatePhases.stream()
                .map(phase -> phase.getStrategy().getCandidates(phase, dirtyAssets))
                .flatMap(blocks -> blocks.stream())
                .filter(block -> block.isPending())
                .collect(Collectors.toList());

        return candidateBlocks;
    }

    public static final void update(TaskStatus taskStatus, Collection<? extends Element> elements) {
        LOGGER.info("Updated with TaskStatus: {}", taskStatus);
        elements.forEach(element -> element.update(taskStatus));
    }

    public static final void restart(Collection<? extends Element> elements) {
        LOGGER.info("Restarting elements: {}", elements);
        elements.forEach(element -> element.restart());
    }

    public static final void forceComplete(Collection<? extends Element> elements) {
        LOGGER.info("Forcing completion of elements: {}", elements);
        elements.forEach(element -> element.forceComplete());
    }

    public static final String getMessage(Element element) {
        return String.format("%s: '%s [%s]' has status: '%s'.",
                element.getClass().getName(), element.getName(), element.getId(), element.getStatus());
    }

    public static final List<String> getErrors(List<String> errors, Collection<? extends Element> elements) {
        elements.forEach(element -> errors.addAll(element.getErrors()));
        return errors;
    }

    public static final Status getStatus(Collection<? extends Element> elements) {
        // Ordering matters throughout this method.  Modify with care.

        if (elements == null) {
            LOGGER.error("Cannot determine status of null elements.");
            return Status.ERROR;
        }

        Status result;
        if (anyHaveStatus(Status.ERROR, elements)) {
            result = Status.ERROR;
            LOGGER.warn("(status={}) Elements contains errors", result);
        } else if (CollectionUtils.isEmpty(elements)) {
            result = Status.COMPLETE;
            LOGGER.warn("(status={}) Empty collection of elements encountered.", result);
        } else if (anyHaveStatus(Status.IN_PROGRESS, elements)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one phase has status: {}", result, Status.IN_PROGRESS);
        } else if (anyHaveStatus(Status.WAITING, elements)) {
            result = Status.WAITING;
            LOGGER.info("(status={}) At least one element has status: {}", result, Status.WAITING);
        } else if (allHaveStatus(Status.COMPLETE, elements)) {
            result = Status.COMPLETE;
            LOGGER.info("(status={}) All elements have status: {}", result, Status.COMPLETE);
        } else if (allHaveStatus(Status.PENDING, elements)) {
            result = Status.PENDING;
            LOGGER.info("(status={}) All elements have status: {}", result, Status.PENDING);
        } else if (anyHaveStatus(Status.COMPLETE, elements)
                && anyHaveStatus(Status.PENDING, elements)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one element has status '{}' and one has status '{}'",
                    result, Status.COMPLETE, Status.PENDING);
        } else {
            result = Status.ERROR;
            LOGGER.error("(status={}) Unexpected state. PlanElements: {}", result, elements);
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
