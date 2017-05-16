package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class provides utilities for tests concerned with {@link Plan}s.
 */
public class PlanTestUtils {

    private PlanTestUtils() {
        // do not instantiate
    }

    public static List<Status> getStepStatuses(Plan plan) {
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .map(Element::getStatus)
                .collect(Collectors.toList());
    }

}
