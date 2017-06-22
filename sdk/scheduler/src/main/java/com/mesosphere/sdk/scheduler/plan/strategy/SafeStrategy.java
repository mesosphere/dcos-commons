package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Step;

import java.util.List;

/**
 * A SafeStrategy requires that the user manually invoke {@link #proceed()} calls in order to move along a deployment.
 * This allows the user to manually check that the rollout looks good at each Step.
 * <p>
 * The default is to do nothing until a first {@link #proceed()} call is received, at which point the first {@link Step}
 * is deployed. The user may then examine whether the deployment of that first {@link Step} was successful before
 * invoking the next {@link #proceed()} call, continuing until the deployment is complete.
 */
public class SafeStrategy extends CanaryStrategy {

    /**
     * Creates a new SafeStrategy based on the Steps given, requiring {@link #proceed()} calls from a user in order for
     * each Step to complete. Initially all pending steps are interrupted.
     *
     * @param steps The child {@link Step}s to complete
     */
    public SafeStrategy(List<Step> steps) {
        super(new SerialStrategy.Generator<Step>().generate(), steps.size(), steps);
    }

}
