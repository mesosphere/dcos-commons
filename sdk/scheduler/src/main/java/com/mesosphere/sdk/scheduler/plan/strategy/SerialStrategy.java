package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A SerialStrategy requires that all Elements are completed in order.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class SerialStrategy<C extends Element> extends InterruptibleStrategy<C> {
    protected DependencyStrategyHelper<C> dependencyStrategyHelper;

    @Override
    public Collection<C> getCandidates(Collection<C> elements, Collection<PodInstanceRequirement> dirtyAssets) {
        // Configure prerequisites before getting candidates:
        return getDependencyStrategyHelper(elements).getCandidates(isInterrupted(), dirtyAssets);
    }

    public StrategyGenerator<C> getGenerator() {
        return new Generator<>();
    }

    private DependencyStrategyHelper<C> getDependencyStrategyHelper(Collection<C> elements) {
        if (dependencyStrategyHelper == null) {
            dependencyStrategyHelper = new DependencyStrategyHelper<>(elements);
            List<C> planElements = elements.stream()
                    .filter(el -> !el.isComplete())
                    .collect(Collectors.toList());
            Collections.reverse(planElements);

            for (int i = 1; i < planElements.size(); i++) {
                C previous = planElements.get(i - 1);
                C current = planElements.get(i);
                dependencyStrategyHelper.addDependency(previous, current);
            }
        }

        return dependencyStrategyHelper;
    }

    /**
     * This class generates Strategy objects of the appropriate type.
     *
     * @param <C> is the type of {@link Element}s to which the Strategy applies.
     */
    public static class Generator<C extends Element> implements StrategyGenerator<C> {
        @Override
        public Strategy<C> generate() {
            return new SerialStrategy<>();
        }
    }
}
