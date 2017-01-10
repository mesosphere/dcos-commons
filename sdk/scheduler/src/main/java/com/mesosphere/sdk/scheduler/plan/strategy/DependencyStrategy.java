package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;

import java.util.Collection;

/**
 * A DependencyStrategy implements the strategy designated by the {@link DependencyStrategyHelper} with which it was
 * constructed.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
@SuppressWarnings("rawtypes")
public class DependencyStrategy<C extends Element> extends InterruptibleStrategy<C> {
    private final DependencyStrategyHelper<C> helper;

    public DependencyStrategy(DependencyStrategyHelper<C> helper) {
        this.helper = helper;
    }

    @Override
    public Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets) {
        return helper.getCandidates(dirtyAssets);
    }
}
