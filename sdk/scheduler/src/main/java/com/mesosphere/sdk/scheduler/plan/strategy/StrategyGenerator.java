package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;

/**
 * A StrategyGenerator generates {@link Strategy} objects of the appropriate type.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
public interface StrategyGenerator<C extends Element> {
    Strategy<C> generate();
}
