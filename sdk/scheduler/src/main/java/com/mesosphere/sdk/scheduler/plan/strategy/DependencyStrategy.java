package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

import java.util.Collection;

/**
 * A DependencyStrategy implements the strategy designated by the {@link DependencyStrategyHelper} with which it was
 * constructed.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public class DependencyStrategy<C extends Element> extends InterruptibleStrategy<C> {
    private final DependencyStrategyHelper<C> helper;

    public DependencyStrategy(DependencyStrategyHelper<C> helper) {
        this.helper = helper;
    }

    @Override
    public Collection<C> getCandidates(Collection<C> elements, Collection<PodInstanceRequirement> dirtyAssets) {
        // Fixed prerequites as defined in the provided helper:
        return helper.getCandidates(isInterrupted(), dirtyAssets);
    }
}
