package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A read-only API for checking whether the service is healthy.
 */
@Path("/v1/health")
public class HealthResource {
  /**
   * We store the PlanManagers, not the underlying Plans, because PlanManagers can change their plans at any time.
   */
  private final Collection<PlanManager> planManagers;

  private final SchedulerConfig schedulerConfig;

  /**
   * Creates a new instance which determines health based on the deploy and recovery plans in the provided plan
   * coordinator.
   */
  public HealthResource(PlanCoordinator planCoordinator, SchedulerConfig schedulerConfig) {
    this(getDeploymentAndRecoveryManagers(planCoordinator), schedulerConfig);
  }

  /**
   * Creates a new instance which determines health based on the provided plans.
   */
  public HealthResource(Collection<PlanManager> planManagers, SchedulerConfig schedulerConfig) {
    this.planManagers = planManagers;
    this.schedulerConfig = schedulerConfig;
  }

  /**
   * Returns the {@link PlanManager}(s) which are marked as being for deployment or for recovery.
   */
  public static Collection<PlanManager> getDeploymentAndRecoveryManagers(
      PlanCoordinator planCoordinator)
  {
    return planCoordinator.getPlanManagers().stream()
        .filter(planManager ->
            planManager.getPlan().isDeployPlan() || planManager.getPlan().isRecoveryPlan()
        )
        .collect(Collectors.toList());
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
    return ResponseUtils.jsonResponse(schedulerConfig.getBuildInfo(), status);
  }
}
