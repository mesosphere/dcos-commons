package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * The DefaultPlanBuilder allows the construction of a {@link Plan} with a custom @{link Strategy} defined by the
 * expressed dependencies.
 */
public class DefaultPlanBuilder extends ElementBuilder<Plan, Phase> {
    public DefaultPlanBuilder(String name) {
        super(name);
    }

    public DefaultPlan build() {
        Set<Phase> phases = dependencyStrategyHelper.getDependencies().keySet();

        return new DefaultPlan(
                name,
                new ArrayList<>(phases),
                new DependencyStrategy<>(dependencyStrategyHelper),
                Collections.emptyList());
    }
}
