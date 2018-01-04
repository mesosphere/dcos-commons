package com.mesosphere.sdk.api;

import java.util.ArrayList;
import java.util.Collection;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.scheduler.plan.PlanManager;

/**
 * A read-only API for checking whether the service is healthy.
 */
@Path("/v1/health")
public class HealthResource {

    private final Collection<PlanManager> planManagers = new ArrayList<>();
    private final Object planManagersLock = new Object();

    /**
     * Assigns the list of plans to be checked for completion when deciding whether the service is healthy.
     *
     * @return this
     */
    public HealthResource setHealthyPlanManagers(Collection<PlanManager> planManagers) {
        synchronized (planManagersLock) {
            this.planManagers.clear();
            this.planManagers.addAll(planManagers);
        }
        return this;
    }

    /**
     * Returns the health of the service as a response code.
     * <ul><li>417 Expectation failed: Errors in plan(s)</li>
     * <li>202 Accepted: Incomplete plan(s)</li>
     * <li>200 OK: All plans complete/no errors</li></ul>
     */
    @GET
    public Response getHealth() {
        final boolean isAnyErrors;
        final boolean isAnyIncomplete;

        synchronized (planManagersLock) {
            isAnyErrors = planManagers.stream()
                    .anyMatch(planManager -> !planManager.getPlan().getErrors().isEmpty());
            isAnyIncomplete = planManagers.stream()
                    .anyMatch(planManager -> !planManager.getPlan().isComplete());
        }

        final Response.Status status;
        if (isAnyErrors) {
            status = Response.Status.EXPECTATION_FAILED;
        } else if (isAnyIncomplete) {
            status = Response.Status.ACCEPTED;
        } else {
            status = Response.Status.OK;
        }
        // In the future, we could return a json object providing details. For now, though, let's just go with something
        // opaque, as there's no guarantee that we wouldn't end up changing this later.
        return ResponseUtils.plainResponse(status.toString(), status);
    }
}
