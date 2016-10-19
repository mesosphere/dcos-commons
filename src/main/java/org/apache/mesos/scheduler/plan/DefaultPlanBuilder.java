package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategy;
import org.apache.mesos.scheduler.plan.strategy.DependencyStrategyHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Created by gabriel on 10/19/16.
 */
public class DefaultPlanBuilder {
    private final DependencyStrategyHelper<Phase> dependencyStrategyHelper = new DependencyStrategyHelper<>();
    private final String name;

    public DefaultPlanBuilder(String name) {
        this.name = name;
    }

    public DefaultPlanBuilder add(Phase phase) throws DependencyStrategyHelper.InvalidDependencyException {
        dependencyStrategyHelper.addElement(phase);
        return this;
    }

    public DefaultPlanBuilder addDependency(Phase child, Phase parent) {
        dependencyStrategyHelper.addDependency(child, parent);
        return this;
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
