package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Created by gabriel on 10/19/16.
 */
public class DefaultPlanBuilder extends ElementBuilder<Phase> {
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
