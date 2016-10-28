package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * The DefaultPhaseBuilder allows the construction of a {@link Phase} with a custom @{link Strategy} defined by the
 * expressed dependencies.
 */
public class DefaultPhaseBuilder extends ElementBuilder<Phase, Step> {
    public DefaultPhaseBuilder(String name) {
        super(name);
    }

    public DefaultPhase build() {
        Set<Step> steps = dependencyStrategyHelper.getDependencies().keySet();

        return new DefaultPhase(
                name,
                new ArrayList<>(steps),
                new DependencyStrategy<>(dependencyStrategyHelper),
                Collections.emptyList());
    }
}
