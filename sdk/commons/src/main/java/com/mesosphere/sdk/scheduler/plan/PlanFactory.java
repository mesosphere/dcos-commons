package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.specification.ServiceSpec;

/**
 * This interface defines the required elements for transforming {@link ServiceSpec}s into {@link Plan}s.
 */
public interface PlanFactory {
    Plan getPlan(ServiceSpec serviceSpec);
}
