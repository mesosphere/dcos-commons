package org.apache.mesos.specification;

import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.specification.yaml.RawServiceSpecification;

import java.util.Collection;

/**
 * Default implementation of {@link PlanGenerator}.
 */
public class DefaultPlanGenerator implements PlanGenerator {
    @Override
    public Collection<Plan> generate(RawServiceSpecification serviceSpec) {
        return null;
    }
}
