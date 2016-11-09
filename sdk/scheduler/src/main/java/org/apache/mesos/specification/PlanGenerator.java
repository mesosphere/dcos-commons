package org.apache.mesos.specification;

import org.apache.mesos.scheduler.plan.Plan;

import java.util.Collection;

/**
 * PlanGenerator generates Plans from a given {@link ServiceSpec}.
 */
public interface PlanGenerator {
    Collection<Plan> generate(ServiceSpec serviceSpec);
}
