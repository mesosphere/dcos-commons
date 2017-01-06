package com.mesosphere.sdk.scheduler.plan;

import java.util.List;

import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

/**
 * A type of {@link Element} which itself is a collection of child {@link Elements}.
 *
 * @param <C> the type of the child elements
 */
public interface ParentElement<C extends Element> extends Element {

    /**
     * Gets the children of this Element.
     */
    List<C> getChildren();

    /**
     * Gets the {@link Strategy} applied to the deployment of this Element's children.
     */
    Strategy<C> getStrategy();
}
