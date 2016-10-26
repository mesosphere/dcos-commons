package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

/**
 * A StrategyGenerator generates {@link Strategy} objects of the appropriate type.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public interface StrategyGenerator<C extends Element> {
    Strategy<C> generate();
}
