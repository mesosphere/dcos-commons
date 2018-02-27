package com.mesosphere.sdk.http.endpoints;

import java.util.Arrays;
import java.util.Collection;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

/**
 * A read-only API for checking whether the service is healthy.
 */
@Path("/v1/health")
public class HealthResource {
    private final Collection<PlanManager> planManagers;

    /**
     * Creates a new instance which determines health based on the deploy and recovery plans in the provided plan
     * coordinator.
     */
    public HealthResource(PlanCoordinator planCoordinator) {
        this(Arrays.asList(
                getDeploymentManager(planCoordinator),
                getRecoveryManager(planCoordinator)));
    }

    /**
     * Creates a new instance which determines health based on the provided plans.
     */
    public HealthResource(Collection<PlanManager> planManagers) {
        this.planManagers = planManagers;
    }

    /**
     * Returns the health of the service as a response code.
     * <ul><li>417 Expectation failed: Errors in plan(s)</li>
     * <li>202 Accepted: Incomplete plan(s)</li>
     * <li>200 OK: All plans complete/no errors</li></ul>
     */
    @GET
    public Response getHealth() {
        boolean isAnyErrors = planManagers.stream()
                .anyMatch(planManager -> !planManager.getPlan().getErrors().isEmpty());
        boolean isAnyIncomplete = planManagers.stream()
                .anyMatch(planManager -> !planManager.getPlan().isComplete());

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

    private static PlanManager getDeploymentManager(PlanCoordinator planCoordinator) {
        return planCoordinator.getPlanManagers().stream()
                .filter(planManager -> planManager.getPlan().isDeployPlan())
                .findFirst().get();
    }

    private static PlanManager getRecoveryManager(PlanCoordinator planCoordinator) {
        return planCoordinator.getPlanManagers().stream()
                .filter(planManager -> planManager.getPlan().isRecoveryPlan())
                .findFirst().get();
    }
}
