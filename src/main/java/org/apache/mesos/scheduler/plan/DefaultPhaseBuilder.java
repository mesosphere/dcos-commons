package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * Created by gabriel on 10/19/16.
 */
public class DefaultPhaseBuilder extends ElementBuilder<Block> {
    public DefaultPhaseBuilder(String name) {
        super(name);
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
