package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.DependencyStrategyHelper;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import java.util.Collection;

/**
 * An ElementBuilder allows the construction of a composite element such that the description of dependencies constructs
 * a custom {@link Strategy}.
 *
 * @param <P> is the type of the Parent {@link Element} which this builder produces.
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public abstract class ElementBuilder<P extends ParentElement<? extends Element>, C extends Element> {
    protected final DependencyStrategyHelper<C> dependencyStrategyHelper = new DependencyStrategyHelper<>();
    protected String name;

    public ElementBuilder(String name) {
        this.name = name;
    }

    public ElementBuilder<P, C> add(C element) throws DependencyStrategyHelper.InvalidDependencyException {
        dependencyStrategyHelper.addElement(element);
        return this;
    }

    public ElementBuilder<P, C> addAll(Collection<C> elements)
            throws DependencyStrategyHelper.InvalidDependencyException {
        for (C element : elements) {
            add(element);
        }
        return this;
    }

    public ElementBuilder<P, C> addDependency(C child, C parent) {
        dependencyStrategyHelper.addDependency(child, parent);
        return this;
    }

    public abstract P build();
}
