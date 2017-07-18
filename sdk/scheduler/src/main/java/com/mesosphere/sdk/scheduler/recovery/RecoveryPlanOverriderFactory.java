package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.state.StateStore;

import java.util.Collection;

/**
 * Implementations of this interface allow the overriding of behavior in the {@link DefaultRecoveryPlanManager}
 * with a custom implementation that addresses applicaiton specific failure recovery mechanisms.
 */
public interface RecoveryPlanOverriderFactory {
    RecoveryPlanOverrider create(StateStore stateStore, Collection<Plan> plans);
}
