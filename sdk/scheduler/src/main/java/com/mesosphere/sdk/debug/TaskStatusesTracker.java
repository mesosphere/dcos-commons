package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.Optional;


//CHECKSTYLE:OFF NestedForDepth

/**
 * TaskStatusesTracker is the backend of TaskStatusesResource.
 * It aggregates taskStatuses (if present) for plans, phases and steps.
 */
public class TaskStatusesTracker implements DebugEndpoint {

  private final StateStore stateStore;

  private final PlanCoordinator planCoordinator;


  public TaskStatusesTracker(PlanCoordinator planCoordinator, StateStore stateStore) {
    this.planCoordinator = planCoordinator;
    this.stateStore = stateStore;
  }


  private JSONArray getTaskStatuses(String filterPlan, String filterPhase, String filterStep) {
    JSONArray planArray = new JSONArray();
    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      if (filterPlan != null && !planManager.getPlan().getName().equalsIgnoreCase(filterPlan))
        continue;
      Plan plan = planManager.getPlan();
      JSONObject planObject = new JSONObject();
      planObject.put("plan", plan.getName());
      for (Phase phase : plan.getChildren()) {
        if (filterPhase != null && !phase.getName().equalsIgnoreCase(filterPhase))
          continue;
        JSONObject phaseObject = new JSONObject();
        for (Step step : phase.getChildren()) {
          if (filterStep != null && !step.getName().equalsIgnoreCase(filterStep))
            continue;
          JSONObject taskStatus = new JSONObject();
          Collection<TaskSpec> tasksInStep = step
              .getPodInstanceRequirement()
              .get().getPodInstance().getPod().getTasks();
          for (TaskSpec taskSpec : tasksInStep) {
            String taskInstanceName = TaskSpec.getInstanceName(
                step.getPodInstanceRequirement().get().getPodInstance(),
                taskSpec.getName()
            );
            Optional<Protos.TaskStatus> status = stateStore.fetchStatus(taskInstanceName);
            taskStatus.put("taskName", taskInstanceName);
            if (status.isPresent()) {
              taskStatus.put("taskId", status.get().getTaskId());
              taskStatus.put("latestTaskState", status.get().getState());
            }
          }
          phaseObject.put(step.getName(), taskStatus);
        }
        planObject.put(phase.getName(), phaseObject);
      }
      planArray.put(planObject);
    }
    return planArray;
  }

  public Response getJson(@QueryParam("plan") String filterPlan,
                          @QueryParam("phase") String filterPhase,
                          @QueryParam("step") String filterStep,
                          @QueryParam("sync") boolean requireSync)
  {
    return ResponseUtils.jsonOkResponse(getTaskStatuses(filterPlan, filterPhase, filterStep));
  }
}
