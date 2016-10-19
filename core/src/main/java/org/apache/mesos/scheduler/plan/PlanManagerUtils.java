package org.apache.mesos.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Commons utility methods for {@code PlanManager}s.
 */
public class PlanManagerUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanManagerUtils.class);

    public static final Status getStatus(Plan plan, Map<UUID, PhaseStrategy> phaseStrategies) {
        // Ordering matters throughout this method.  Modify with care.

        Status result;
        if (plan != null && CollectionUtils.isNotEmpty(plan.getErrors())) {
            result = Status.ERROR;
            LOGGER.warn("(status={}) Plan contains errors", result);
        } else if (plan == null || CollectionUtils.isEmpty(plan.getPhases())) {
            result = Status.COMPLETE;
            LOGGER.warn("(status={}) Plan doesn't have any phases", result);
        } else if (anyHaveStatus(Status.IN_PROGRESS, plan, phaseStrategies)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one phase has status: {}", result, Status.IN_PROGRESS);
        } else if (anyHaveStatus(Status.WAITING, plan, phaseStrategies)) {
            result = Status.WAITING;
            LOGGER.info("(status={}) At least one phase has status: {}", result, Status.WAITING);
        } else if (allHaveStatus(Status.COMPLETE, plan, phaseStrategies)) {
            result = Status.COMPLETE;
            LOGGER.info("(status={}) All phases have status: {}", result, Status.COMPLETE);
        } else if (allHaveStatus(Status.PENDING, plan, phaseStrategies)) {
            result = Status.PENDING;
            LOGGER.info("(status={}) All phases have status: {}", result, Status.PENDING);
        } else if (anyHaveStatus(Status.COMPLETE, plan, phaseStrategies)
                && anyHaveStatus(Status.PENDING, plan, phaseStrategies)) {
            result = Status.IN_PROGRESS;
            LOGGER.info("(status={}) At least one phase has status '{}' and one has status '{}'",
                    result, Status.COMPLETE, Status.PENDING);
        } else {
            result = null;
            LOGGER.error("(status={}) Unexpected state. Plan: {}", result, plan);
        }
        return result;
    }

    public static boolean allHaveStatus(Status status, Plan plan, Map<UUID, PhaseStrategy> phaseStrategies) {
        final List<? extends Phase> phases = plan.getPhases();
        return phases
                .stream()
                .allMatch(phase -> phaseStrategies.containsKey(phase.getId())
                        && phaseStrategies.get(phase.getId()).getStatus() == status);
    }

    public static boolean anyHaveStatus(Status status, Plan plan, Map<UUID, PhaseStrategy> phaseStrategies) {
        final List<? extends Phase> phases = plan.getPhases();
        return phases
                .stream()
                .anyMatch(phase -> phaseStrategies.containsKey(phase.getId())
                        && phaseStrategies.get(phase.getId()).getStatus() == status);
    }
}
