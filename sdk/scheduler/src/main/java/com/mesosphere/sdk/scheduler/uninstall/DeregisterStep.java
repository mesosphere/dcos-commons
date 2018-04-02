package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.Optional;

/**
 * Step which advertises that the service has been deregistered and has completed uninstall. This is the last step in an
 * uninstall plan, and it acts as a gate on advertising a completed uninstall to Cosmos. After this step is complete,
 * the scheduler process should soon be destroyed.
 */
public class DeregisterStep extends UninstallStep {

    public DeregisterStep() {
        super("deregister", Status.PENDING);
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        if (isPending()) {
            setStatus(Status.PREPARED);
        }

        return getPodInstanceRequirement();
    }

    /**
     * Marks this step complete after the framework has been deregistered.
     * At this point, the overall {@code deploy} plan for uninstall should be complete, and the Scheduler process should
     * be destroyed by DC/OS soon after.
     */
    public void setComplete() {
        setStatus(Status.COMPLETE);
    }
}
