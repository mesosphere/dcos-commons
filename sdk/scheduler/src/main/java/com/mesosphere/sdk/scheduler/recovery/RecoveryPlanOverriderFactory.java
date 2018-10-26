package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.state.StateStore;

import java.util.Collection;

/**
 * Implementations of this interface allow the overriding of behavior in the {@link DefaultRecoveryPlanManager}
 * with a custom implementation that addresses applicaiton specific failure recovery mechanisms.
 */
public interface RecoveryPlanOverriderFactory {

  /**
   * Returns a new {@link RecoveryPlanOverrider} which will be queried for custom overridden phases when task recovery
   * occurs.
   *
   * @param stateStore the state store being used by the service
   * @param plans      all plans being used by the service, as of the time that the service is being initialized
   */
  RecoveryPlanOverrider create(StateStore stateStore, Collection<Plan> plans);
}
