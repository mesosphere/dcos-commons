package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * ReservationsTracker is the backend of the ReservationsDebugEndpoint.
 */
public class ReservationsTracker implements DebugEndpoint {

  private PlanCoordinator planCoordinator;

  public ReservationsTracker(PlanCoordinator planCoordinator) {
    this.planCoordinator = planCoordinator;
  }

  public Response getJson(@QueryParam("plan") String fitleredPlan,
                          @QueryParam("phase") String filteredPhase,
                          @QueryParam("step") String filteredStep,
                          @QueryParam("sync") boolean sync)
  {

    JSONObject outcome = new JSONObject();

    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      Plan plan = planManager.getPlan();
      outcome.put(plan.getName(), plan.getStatus().name());
    }

    return ResponseUtils.jsonOkResponse(outcome);
  }
}
