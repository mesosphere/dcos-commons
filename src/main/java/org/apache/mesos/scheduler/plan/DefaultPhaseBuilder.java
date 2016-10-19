package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategy;
import org.apache.mesos.scheduler.plan.strategy.DependencyStrategyHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Created by gabriel on 10/19/16.
 */
public class DefaultPhaseBuilder {
    private final DependencyStrategyHelper<Block> dependencyStrategyHelper = new DependencyStrategyHelper<>();
    private final String name;

    public DefaultPhaseBuilder(String name) {
        this.name = name;
    }

    public DefaultPhaseBuilder add(Block block) throws DependencyStrategyHelper.InvalidDependencyException {
        dependencyStrategyHelper.addElement(block);
        return this;
    }

    public DefaultPhaseBuilder addDependency(Block parent, Block child) {
        dependencyStrategyHelper.addDependency(parent, child);
        return this;
    }

    public DefaultPhase build() {
        Set<Block> blocks = dependencyStrategyHelper.getDependencies().keySet();

        return new DefaultPhase(
                name,
                new ArrayList<>(blocks),
                new DependencyStrategy<>(dependencyStrategyHelper),
                Collections.emptyList());
    }
}
