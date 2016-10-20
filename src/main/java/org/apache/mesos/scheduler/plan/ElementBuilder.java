package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.plan.strategy.DependencyStrategyHelper;

/**
 * Created by gabriel on 10/19/16.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public abstract class ElementBuilder<C extends Element> {
    protected final DependencyStrategyHelper<C> dependencyStrategyHelper = new DependencyStrategyHelper<>();
    protected String name;

    public ElementBuilder(String name) {
        this.name = name;
    }

    public ElementBuilder add(C element) throws DependencyStrategyHelper.InvalidDependencyException {
        dependencyStrategyHelper.addElement(element);
        return this;
    }

    public ElementBuilder addDependency(C child, C parent) {
        dependencyStrategyHelper.addDependency(child, parent);
        return this;
    }
}
