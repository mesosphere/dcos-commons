package com.mesosphere.sdk.scheduler.plan.strategy;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TerminalStrategy<C extends Element> extends InterruptibleStrategy<C> {
    private DependencyStrategyHelper<C> dependencyStrategyHelper;
    boolean parallel;
    C terminalElement;

    public TerminalStrategy(boolean isParallel, C te) {
        this.parallel = isParallel;
        this.terminalElement = te;
    }

    @Override
    public Collection<C> getCandidates(Collection<C> elements, Collection<PodInstanceRequirement> dirtyAssets) {
        Collection<C> candidates = getDependencyStrategyHelper(elements).getCandidates(isInterrupted(), dirtyAssets);
        //return getDependencyStrategyHelper(elements).getCandidates(isInterrupted(), dirtyAssets);
        return candidates;
    }

    private DependencyStrategyHelper<C> getDependencyStrategyHelper(Collection<C> elements) {
        if (parallel) {
            if (dependencyStrategyHelper == null) {
                dependencyStrategyHelper = new DependencyStrategyHelper<>(elements);
                for (C element: dependencyStrategyHelper.getDependencies().keySet()) {
                    if (element != terminalElement) {
                        dependencyStrategyHelper.addDependency(terminalElement, element);
                    }
                }
            }
        } else {
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
                C penultimateElement = planElements.get(planElements.size() - 1);
                dependencyStrategyHelper.addDependency(terminalElement, penultimateElement);
            }
        }
        return dependencyStrategyHelper;
    }

    @VisibleForTesting
    public boolean isParallel() {
        return parallel;
    }

    public static class Generator<C extends Element> implements StrategyGenerator<C> {
        TerminalStrategy<C> strategy;
        public Generator(boolean parallel, C te) {
            strategy = new TerminalStrategy<>(parallel, te);
        }

        @Override
        public Strategy<C> generate() {
            return strategy;
        }
    }

}

