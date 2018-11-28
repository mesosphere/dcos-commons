package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;


import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.Collection;

/*
 * TaskStatusesTracker is the backend of the TaskInfoDebugEndpoint.
 * it aggregates taskInfos for all the plans and allows for filtering based on phase and step
 */

public class TaskStatusesTracker implements DebugEndpoint {


  private final StateStore stateStore;
  private final PlanCoordinator planCoordinator;


  public TaskStatusesTracker(PlanCoordinator planCoordinator, StateStore stateStore) {
    this.planCoordinator = planCoordinator;
    this.stateStore = stateStore;
  }


  private JSONArray getTaskInfos() {
    JSONArray taskArray = new JSONArray();
    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      Plan plan = planManager.getPlan();
      for (Phase phase : plan.getChildren()) {
        //Filter down to a phase if specified.
        for (Step step : phase.getChildren()) {
          Collection<String> taskInStep = step.getPodInstanceRequirement().get().getTasksToLaunch();
          JSONObject taskStatus = new JSONObject();
          taskStatus.put("step", step.getName());
          taskStatus.put("tasksInStep", taskInStep);
          taskArray.put(taskStatus);
        }
      }
    }
    return taskArray;
  }

  public Response getJson(@QueryParam("plan") String filterPlan,
                          @QueryParam("phase") String filterPhase,
                          @QueryParam("step") String filterStep,
                          @QueryParam("sync") boolean requireSync)
  {
    return ResponseUtils.jsonOkResponse(getTaskInfos());

  }



}
