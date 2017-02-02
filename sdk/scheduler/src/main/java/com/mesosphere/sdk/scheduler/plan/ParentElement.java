package com.mesosphere.sdk.scheduler.plan;

import java.util.List;
import java.util.Map;

import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

/**
 * A type of {@link Element} which itself is a collection of child {@link Element}s.
 *
 * @param <C> the type of the child elements
 */
public interface ParentElement<C extends Element> extends Element, Interruptible {

    /**
     * Gets the children of this Element.
     */
    List<C> getChildren();

    /**
     * Gets the {@link Strategy} applied to the deployment of this Element's children.
     */
    Strategy<C> getStrategy();

    @Override
    default void interrupt() {
        getStrategy().interrupt();
    }

    @Override
    default void proceed() {
        getStrategy().proceed();
    }

    default boolean isInterrupted() {
        return getStrategy().isInterrupted();
    }

    @Override
    default void updateParameters(Map<String, String> parameters) {
        for (C child : getChildren()) {
            child.updateParameters(parameters);
        }
    }
}
