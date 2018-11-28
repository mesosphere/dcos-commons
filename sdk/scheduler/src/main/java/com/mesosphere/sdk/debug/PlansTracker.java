package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 * PlansTracker is the backend of the PlansDebugEndpoint.
 * It aggregates the status across all Plans and allows
 * for filtered responses. The PlansTracker also returns
 * the currently active plans and the current state the
 * the scheduler is in.
 */
public class PlansTracker implements DebugEndpoint {

  private final StateStore stateStore;

  private final PlanCoordinator planCoordinator;

  public PlansTracker(PlanCoordinator planCoordinator, StateStore stateStore) {
    this.planCoordinator = planCoordinator;
    this.stateStore = stateStore;
  }

  @SuppressWarnings("checkstyle:NestedForDepthCheck")
  private HashMap<String, JSONArray> generateServiceTopology() {

    HashMap<String, JSONArray> plansTree = new HashMap<String, JSONArray>();
    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      Plan plan = planManager.getPlan();
      JSONArray phasesArray = new JSONArray();
      for (Phase phase : plan.getChildren()) {
        HashMap<String, JSONArray> phaseTree = new HashMap<String, JSONArray>();
        JSONArray stepsArray = new JSONArray();
        for (Step step : phase.getChildren()) {
          stepsArray.put(step.getName());
        }
        phaseTree.put(phase.getName(), stepsArray);
        phasesArray.put(phaseTree);
      }
      plansTree.put(plan.getName(), phasesArray);
    }
    return plansTree;
  }

  @SuppressWarnings({"checkstyle:MultipleStringLiteralsCheck", "checkstyle:NestedForDepthCheck"})
  private JSONObject generateServiceStatus(String filterPlan,
                                           String filterPhase,
                                           String filterStep)
  {
    JSONObject outcome = new JSONObject();
    JSONArray plansArray = new JSONArray();

    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      Plan plan = planManager.getPlan();

      //Filter down to a plan if specified.
      if (filterPlan != null && !plan.getName().equalsIgnoreCase(filterPlan))
        continue;

      JSONObject planObject = new JSONObject();
      JSONArray phaseArray = new JSONArray();
      planObject.put("name", plan.getName());
      planObject.put("status", plan.getStatus());
      planObject.put("strategy", plan.getStrategy().getName());
      planObject.put("phases", phaseArray);

      //Get a rollup aggregation of the steps.
      int totalSteps = plan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .collect(Collectors.toSet())
          .size();
      int completedSteps = plan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .filter(step -> step.isComplete())
          .collect(Collectors.toSet())
          .size();

      planObject.put("total-steps", totalSteps);
      planObject.put("completed-steps", completedSteps);

      //Iterate over phases.
      for (Phase phase : plan.getChildren()) {
        //Filter down to a phase if specified.
        if (filterPhase != null && !phase.getName().equalsIgnoreCase(filterPhase))
          continue;

        JSONObject phaseObject = new JSONObject();
        JSONArray stepArray = new JSONArray();

        phaseObject.put("name", phase.getName());
        phaseObject.put("status", phase.getStatus());
        phaseObject.put("strategy", phase.getStrategy().getName());
        phaseObject.put("steps", stepArray);

        //Iterate over steps.
        for (Step step : phase.getChildren()) {
          //Filter down to a step if specified.
          if (filterStep != null && !step.getName().equalsIgnoreCase(filterStep))
            continue;

          JSONObject stepObject = new JSONObject();
          stepObject.put("name", step.getName());
          stepObject.put("status", step.getStatus());
          stepObject.put("errors", step.getErrors());

          stepArray.put(stepObject);
        }

        phaseArray.put(phaseObject);
      }

      plansArray.put(planObject);
    }
    outcome.put("plans", plansArray);
    return outcome;
  }

  @SuppressWarnings({"checkstyle:ReturnCountCheck", "checkstyle:MultipleStringLiteralsCheck"})
  private Optional<JSONObject> getValidationErrorResponse(String filterPlan,
                                                String filterPhase,
                                                String filterStep)
  {
    //If a step is defined ensure both plan and phase are also provided.
    if (filterStep != null && (filterPlan == null || filterPhase == null)) {
      JSONObject outcome = new JSONObject();
      outcome.put("invalid_input", "Step specified without parent Phase and Plan values.");
      return Optional.of(outcome);
    }

    //If a phase is defined ensure parent plan is also provided.
    if (filterPhase != null && filterPlan == null) {
      JSONObject outcome = new JSONObject();
      outcome.put("invalid_input", "Phase specified without parent Plan.");
      return Optional.of(outcome);
    }

    //Ensure correct ownership. Start with the plan.

    //If no explicit plan defined, nothing further to do.
    if (filterPlan == null) return null;

    //Plan is specified, ensure it exists within our list of plans.
    //Filter for our desired plan.
    List<PlanManager> planManagers = planCoordinator.getPlanManagers().stream()
        .filter(planManger -> planManger.getPlan().getName().equalsIgnoreCase(filterPlan))
        .collect(Collectors.toList());
    //Ensure we found our plan.
    if (planManagers.size() != 1) {
      JSONObject outcome = new JSONObject();
      outcome.put("invalid_input",
          "Supplied plan not found in list of all available plans!");
      return Optional.of(outcome);
    }

    //If no explicit phase defined, nothing further to do.
    if (filterPhase == null) return null;

    Plan plan = planManagers.get(0).getPlan();

    //Ensure our phase is found in the plan.
    List<Phase> phaseList = plan.getChildren().stream()
        .filter(phases -> phases.getName().equalsIgnoreCase(filterPhase))
        .collect(Collectors.toList());

    //Ensure we found our phase.
    if (phaseList.size() != 1) {
      JSONObject outcome = new JSONObject();
      outcome.put("invalid_input",
          "Supplied phase not found in set of possible phases with supplied plan!");
      return Optional.of(outcome);
    }

    //If no explicit step defined, nothing further to do.
    if (filterStep == null) return null;

    Phase phase = phaseList.get(0);
    List<Step> stepList = phase.getChildren().stream()
        .filter(steps -> steps.getName().equalsIgnoreCase(filterStep))
        .collect(Collectors.toList());

    //Ensure we found our phase.
    if (stepList.size() != 1) {
      JSONObject outcome = new JSONObject();
      outcome.put("invalid_input",
          "Supplied step not found in set of possible steps with supplied plan and phase!");
      return Optional.of(outcome);
    }

    //Successfully found plan, phase and step.
    return Optional.empty();
  }

  public Response getJson(@QueryParam("plan") String filterPlan,
                        @QueryParam("phase") String filterPhase,
                        @QueryParam("step") String filterStep,
                        @QueryParam("sync") boolean requireSync)
  {

    //Validate plan/phase/step if provided.
    if (filterPlan != null || filterPhase != null || filterStep != null) {
      Optional<JSONObject> validationOutcome = getValidationErrorResponse(filterPlan,
                                                                          filterPhase,
                                                                          filterStep);
      if (validationOutcome.isPresent())
        return ResponseUtils.jsonOkResponse(validationOutcome.get());
    }

    //At this point we're either returning the entire plans tree or
    //pruning it down to a plan/phase/step which has been validated.

    JSONObject response = new JSONObject();
    response.put("service-topology", generateServiceTopology());
    response.put("service-status", generateServiceStatus(filterPlan, filterPhase, filterStep));

    //Retrieve the latest Plans from PlanCoordinator. Some plans i.e recovery do change over time.
    HashMap<String, Plan> planMap = new HashMap<String, Plan>();
    //Add all the plans for easy lookups below.
    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      Plan plan = planManager.getPlan();
      planMap.put(plan.getName(), plan);
    }

    //Set scheduler to one of DEPLOYING, DEPLOYED, RECOVERING or DECOMISSIONING.
    //If unknown, set to generic status to RUNNING when running custom-plans.
    String schedulerStatus = "RUNNING";
    if (planMap.containsKey(Constants.DEPLOY_PLAN_NAME) &&
        planMap.get(Constants.DEPLOY_PLAN_NAME).isRunning())
      schedulerStatus = "DEPLOYING";
    //Here check the StateStore BEFORE the local-cache.
    if (StateStoreUtils.getDeploymentWasCompleted(stateStore) ||
        (planMap.containsKey(Constants.DEPLOY_PLAN_NAME) &&
            planMap.get(Constants.DEPLOY_PLAN_NAME).isComplete()))
      schedulerStatus = "DEPLOYED";
    if (planMap.containsKey(Constants.RECOVERY_PLAN_NAME) &&
        planMap.get(Constants.RECOVERY_PLAN_NAME).isRunning())
      schedulerStatus = "RECOVERING";
    if (planMap.containsKey(Constants.DECOMMISSION_PLAN_NAME) &&
        planMap.get(Constants.DECOMMISSION_PLAN_NAME).isRunning())
      schedulerStatus = "DECOMMISIONING";
    response.put("scheduler-state", schedulerStatus);

    //List all active plans.
    List<String> activePlans = planMap.entrySet().stream()
        .filter(entry -> entry.getValue().isRunning())
        .map(entry -> entry.getKey())
        .collect(Collectors.toList());
    response.put("active-plans", new JSONArray(activePlans));

    return ResponseUtils.jsonOkResponse(response);
  }
}
