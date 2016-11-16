package org.apache.mesos.scheduler.plan;

import org.apache.mesos.specification.ServiceSpec;

/**
 * This interface defines the required elements for transforming {@link ServiceSpec}s into {@link Plan}s.
 */
public interface PlanFactory {
    Plan getPlan(ServiceSpec serviceSpec);
}
