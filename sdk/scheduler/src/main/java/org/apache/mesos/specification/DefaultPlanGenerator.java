package org.apache.mesos.specification;

import org.apache.mesos.scheduler.plan.Plan;

import java.util.Collection;

/**
 * Default implementation of {@link PlanGenerator}.
 */
public class DefaultPlanGenerator implements PlanGenerator {
    @Override
    public Collection<Plan> generate(ServiceSpec serviceSpec) {
        return null;
    }
}
