package com.mesosphere.sdk.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


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

  //CHECKSTYLE:OFF NestedForDepth

  private List<PlanResponse> getTaskStatuses(String filterPlan, String filterPhase, String filterStep) {
    List<PlanResponse> planArray = new ArrayList<>();
    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      if (filterPlan != null && !planManager.getPlan().getName().equalsIgnoreCase(filterPlan)) {
        continue;
      }
      Plan plan = planManager.getPlan();
      PlanResponse planObject = new PlanResponse();
      planObject.setName(plan.getName());
      for (Phase phase : plan.getChildren()) {
        if (filterPhase != null && !phase.getName().equalsIgnoreCase(filterPhase)) {
          continue;
        }
        PhaseResponse phaseObject = new PhaseResponse();
        phaseObject.setName(phase.getName());
        for (Step step : phase.getChildren()) {
          StepResponse stepObject = new StepResponse();
          if (filterStep != null && !step.getName().equalsIgnoreCase(filterStep)) {
            continue;
          }
          Collection<TaskSpec> tasksInStep = step
              .getPodInstanceRequirement()
              .get().getPodInstance().getPod().getTasks();
          for (TaskSpec taskSpec : tasksInStep) {
            //filter tasks in pod belonging to this step
            TaskStatusResponse taskStatusObject = new TaskStatusResponse();
            if (step.getName().contains(taskSpec.getName())) {
              String taskInstanceName = TaskSpec.getInstanceName(
                  step.getPodInstanceRequirement().get().getPodInstance(),
                  taskSpec.getName()
              );
              Optional<Protos.TaskStatus> status = stateStore.fetchStatus(taskInstanceName);
              taskStatusObject.setName(taskInstanceName);
              if (status.isPresent()) {
                taskStatusObject.setTaskId(status.get().getTaskId());
                taskStatusObject.setTaskStatus(status.get().getState());
              }
              stepObject.setName(step.getName());
              stepObject.addTaskStatus(taskStatusObject);
            }
          }
          phaseObject.addStep(stepObject);
        }
        planObject.addPhase(phaseObject);
      }
      planArray.add(planObject);
    }
    return planArray;
  }

  //CHECKSTYLE:ON NestedForDepth

  public Response getJson(@QueryParam("plan") String filterPlan,
                          @QueryParam("phase") String filterPhase,
                          @QueryParam("step") String filterStep,
                          @QueryParam("sync") boolean requireSync)
  {
    ObjectMapper jsonMapper = new ObjectMapper();
    jsonMapper.registerModule(new Jdk8Module());
    jsonMapper.registerModule(new JsonOrgModule());

    List<PlanResponse> serviceResponse = getTaskStatuses(filterPlan, filterPhase, filterStep);
    JSONObject response = jsonMapper.convertValue(serviceResponse, JSONObject.class);

    return ResponseUtils.jsonOkResponse(response);
  }

  /**
   * Captures metadata for an individual taskStatus
   */
  private class TaskStatusResponse {

    private String name;

    private Protos.TaskState taskState;

    private Protos.TaskID taskId;

    public TaskStatusResponse() {
      this.name = "";
      this.taskState = Protos.TaskState.TASK_UNKNOWN;
      this.taskId = Protos.TaskID.getDefaultInstance();
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setTaskStatus(Protos.TaskState taskState) {
      this.taskState = taskState;
    }

    public void setTaskId(Protos.TaskID taskId) {
      this.taskId = taskId;
    }
  }

  /**
   * Captures metadata for an individual Step containing a set of TaskStatuses
   */
  private class StepResponse {

    private String name = "";

    private List<TaskStatusResponse> tasks = new ArrayList<>();


    public void setName(String name) {
      this.name = name;
    }

    public void addTaskStatus(TaskStatusResponse task) {
      this.tasks.add(task);
    }
  }

  /**
   * Captures metadata for an individual Phase containing a set of Steps.
   */
  private class PhaseResponse {
    private  String name = "";

    private  List<StepResponse> steps = new ArrayList<>();

    public void addStep(StepResponse stepResponse) {
      this.steps.add(stepResponse);
    };

    public void setName(String name) {
      this.name = name;
    }
  }

  /**
   * Captures metadata for an individual Plan containing a set of Phases.
   */
  private class PlanResponse {
    private String name = "";

    private List<PhaseResponse> phases = new ArrayList<>();

    public void setName(String name) {
      this.name = name;
    }

    public void addPhase(PhaseResponse phase) {
      this.phases.add(phase);
    }
  }
}
