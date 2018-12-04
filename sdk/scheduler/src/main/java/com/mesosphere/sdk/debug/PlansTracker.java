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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * PlansTracker is the backend of the PlansDebugEndpoint.
 * It aggregates the status across all Plans and allows
 * for filtered responses. The PlansTracker also returns
 * the currently active plans and the current state the
 * the scheduler is in.
 */
public class PlansTracker implements DebugEndpoint {

  /**
   * Encapsulates the current state the scheduler is in.
   */
  public enum SchedulerState {
    RUNNING,
    DEPLOYED,
    DEPLOYING,
    RECOVERING,
    DECOMMISSIONING
  }

  private final StateStore stateStore;

  private final PlanCoordinator planCoordinator;

  public PlansTracker(PlanCoordinator planCoordinator, StateStore stateStore) {
    this.planCoordinator = planCoordinator;
    this.stateStore = stateStore;
  }

  private Optional<String> getValidationErrorResponse(String filterPlan,
                                                String filterPhase,
                                                String filterStep)
  {
    //Start with the assumption that the input is valid. "Valid till proven guilty"
    Optional<String> invalidInputReason = Optional.empty();

    //If a step is defined ensure both plan and phase are also provided.
    if (filterStep != null && (filterPlan == null || filterPhase == null)) {
      invalidInputReason = Optional.of("Step specified without parent Phase and Plan values.");
    }

    //If a phase is defined ensure parent plan is also provided.
    if (!invalidInputReason.isPresent() && filterPhase != null && filterPlan == null) {
      invalidInputReason = Optional.of("Phase specified without parent Plan.");
    }

    //Ensure correct ownership. Start with the plan.
    
    //Keep a pointer here so we don't constantly traverse the tree.
    List<PlanManager> planManagers = null;

    //If no explicit plan defined, nothing further to do.
    if (!invalidInputReason.isPresent() && filterPlan != null) {

      planManagers = planCoordinator.getPlanManagers().stream()
        .filter(planManger -> planManger.getPlan().getName().equalsIgnoreCase(filterPlan))
        .collect(Collectors.toList());

      //Plan is specified, ensure it exists within our list of plans.
      boolean planFound = planManagers.size() == 1;
      if (!planFound) {
        invalidInputReason = Optional.of("Supplied plan not found in list of all available plans!");
      }
    }

    List<Phase> phaseList = null;
    //If no explicit phase defined, nothing further to do.
    if (!invalidInputReason.isPresent() && filterPhase != null) {
      //Ensure our phase is found in the plan.
      Plan plan = planManagers.get(0).getPlan();
      phaseList = plan.getChildren().stream()
        .filter(phases -> phases.getName().equalsIgnoreCase(filterPhase))
        .collect(Collectors.toList());
      boolean phaseFound = phaseList.size() == 1;
      if (!phaseFound) {
        invalidInputReason = Optional.of(
            "Supplied phase not found in set of possible phases with supplied plan!");
      }
    }

    //If no explicit step defined, nothing further to do.
    if (!invalidInputReason.isPresent() && filterStep != null) {
      Phase phase = phaseList.get(0);
      boolean stepFound = phase.getChildren().stream()
          .filter(steps -> steps.getName().equalsIgnoreCase(filterStep))
          .count() == 1;
      if (!stepFound) {
        invalidInputReason = Optional.of(
            "Supplied step not found in set of possible steps with supplied plan and phase!");
      }
    }

    return invalidInputReason;
  }

  public Response getJson(@QueryParam("plan") String filterPlan,
                        @QueryParam("phase") String filterPhase,
                        @QueryParam("step") String filterStep,
                        @QueryParam("sync") boolean requireSync)
  {

    //Validate plan/phase/step if provided.
    if (filterPlan != null || filterPhase != null || filterStep != null) {
      Optional<String> validationOutcome = getValidationErrorResponse(filterPlan,
          filterPhase,
          filterStep);
      if (validationOutcome.isPresent()) {
        JSONObject response = new JSONObject();
        response.put("invalid-input", validationOutcome.get());
        return ResponseUtils.jsonOkResponse(response);
      }
    }

    //At this point we're either returning the entire plans tree or
    //pruning it down to a plan/phase/step which has been validated.

    SerializePlansTracker plansTracker = generateServiceStatus(filterPlan,
        filterPhase,
        filterStep);

    ObjectMapper jsonMapper = new ObjectMapper();
    jsonMapper.registerModule(new Jdk8Module());
    jsonMapper.registerModule(new JsonOrgModule());
    JSONObject response = jsonMapper.convertValue(plansTracker, JSONObject.class);
    return ResponseUtils.jsonOkResponse(response);
  }

  public SerializePlansTracker generateServiceStatus(String filterPlan,
                                                         String filterPhase,
                                                         String filterStep)
  {
    List<SerializeElement> plansTopology = new ArrayList<>();
    List<SerializePlan> plansList = new ArrayList<>();
    List<String> activePlans = new ArrayList<>();
    HashMap<String, Plan> planMap = new HashMap<>();

    for (PlanManager planManager : planCoordinator.getPlanManagers()) {
      Plan plan = planManager.getPlan();

      planMap.put(plan.getName(), plan);
      if (plan.isRunning()) {
        activePlans.add(plan.getName());
      }

      //Generate topology for this plan.
      plansTopology.add(generatePlanTopology(plan));

      //Filter down to a plan if specified.
      if (filterPlan != null && !plan.getName().equalsIgnoreCase(filterPlan)) {
        continue;
      }

      SerializePlan serializedPlan = generatePlanServiceStatus(plan,
          filterPhase,
          filterStep);

      plansList.add(serializedPlan);
    }

    //If unknown, set to generic status to RUNNING when running custom-plans.
    SchedulerState schedulerState = SchedulerState.RUNNING;
    if (planMap.containsKey(Constants.DEPLOY_PLAN_NAME) &&
        planMap.get(Constants.DEPLOY_PLAN_NAME).isRunning())
    {
      schedulerState = SchedulerState.DEPLOYING;
    }
    //Here check the StateStore BEFORE the local-cache.
    if (StateStoreUtils.getDeploymentWasCompleted(stateStore) ||
        (planMap.containsKey(Constants.DEPLOY_PLAN_NAME) &&
            planMap.get(Constants.DEPLOY_PLAN_NAME).isComplete()))
    {
      schedulerState = SchedulerState.DEPLOYED;
    }
    if (planMap.containsKey(Constants.RECOVERY_PLAN_NAME) &&
        planMap.get(Constants.RECOVERY_PLAN_NAME).isRunning())
    {
      schedulerState = SchedulerState.RECOVERING;
    }
    if (planMap.containsKey(Constants.DECOMMISSION_PLAN_NAME) &&
        planMap.get(Constants.DECOMMISSION_PLAN_NAME).isRunning())
    {
      schedulerState = SchedulerState.DECOMMISSIONING;
    }

    return new SerializePlansTracker(schedulerState, activePlans, plansList, plansTopology);
  }

  private SerializeElement generatePlanTopology(Plan plan) {
    List<SerializeElement> phaseList = new ArrayList<>();

    for (Phase phase : plan.getChildren()) {
      SerializeElement phaseTopology = generatePhaseTopology(phase);
      phaseList.add(phaseTopology);
    }
    return new SerializeElement(plan.getName(), "plan", Optional.of(phaseList));
  }

  private SerializeElement generatePhaseTopology(Phase phase) {
    List<SerializeElement> stepList = new ArrayList<>();
    for (Step step : phase.getChildren()) {
      stepList.add(new SerializeElement(step.getName(), "step", Optional.empty()));
    }
    return new SerializeElement(phase.getName(), "phase", Optional.of(stepList));
  }

  private SerializePlan generatePlanServiceStatus(Plan plan,
                                                  String filterPhase,
                                                  String filterStep)
  {

    List<SerializePhase> phasesList = new ArrayList<>();

    for (Phase phase : plan.getChildren()) {
      //Filter down to a phase if specified
      if (filterPhase != null && !phase.getName().equalsIgnoreCase(filterPhase)) {
        continue;
      }

      SerializePhase phaseElement = generatePhaseServiceStatus(
          phase,
          filterStep);
      phasesList.add(phaseElement);
    }

    int totalSteps = plan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .collect(Collectors.toSet())
        .size();

    int completedSteps = plan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isComplete())
        .collect(Collectors.toSet())
        .size();

    return new SerializePlan(plan.getName(),
        plan.getStatus().toString(),
        plan.getStrategy().getName(),
        phasesList,
        totalSteps,
        completedSteps);
  }


  private SerializePhase generatePhaseServiceStatus(Phase phase, String filterStep) {
    List<SerializeStep> stepsList = new ArrayList<>();

    for (Step step : phase.getChildren()) {
      //Filter down to a phase if specified
      if (filterStep != null && !step.getName().equalsIgnoreCase(filterStep)) {
        continue;
      }
      stepsList.add(new SerializeStep(
          step.getName(),
          step.getStatus().toString(),
          step.getErrors()));
    }

    return new SerializePhase(
        phase.getName(),
        phase.getStatus().toString(),
        phase.getStrategy().getName(),
        stepsList);
  }

  /**
   *  This class is used to capture the basics of a {@link Step}.
   */
  public static class SerializeStep {

    private final String name;

    private final String status;

    private final List<String> errors;

    public SerializeStep(String name,
                            String status,
                            List<String> errors)
    {
      this.name = name;
      this.status = status;
      this.errors = errors;
    }

    public String getName() {
      return this.name;
    }

    public String getStatus() {
      return this.status;
    }

    public List<String> getErrors() {
      return this.errors;
    }
  }

  /**
   *  This class is used to capture the basics of a {@link Phase}.
   */
  public static class SerializePhase {

    private final String name;

    private final String status;

    private final String strategy;

    private final List<SerializeStep> steps;

    public SerializePhase(String name, String status, String strategy, List<SerializeStep> steps) {
      this.name = name;
      this.status = status;
      this.strategy = strategy;
      this.steps = steps;
    }

    public String getName() {
      return this.name;
    }

    public String getStrategy() {
      return this.strategy;
    }

    public String getStatus() {
      return this.status;
    }

    public List<SerializeStep> getSteps() {
      return this.steps;
    }
  }

  /**
   *  This class is used to capture the basics of a {@link Plan}.
   */
  public static class SerializePlan {

    private final String name;

    private final String status;

    private final String strategy;

    private final List<SerializePhase> phases;

    private final int totalSteps;

    private final int completedSteps;

    public SerializePlan(String name,
                         String status,
                         String strategy,
                         List<SerializePhase> phases,
                         int totalSteps,
                         int completedSteps)
    {
      this.name = name;
      this.status = status;
      this.strategy = strategy;
      this.phases = phases;
      this.totalSteps = totalSteps;
      this.completedSteps = completedSteps;
    }

    public String getName() {
      return this.name;
    }

    public String getStatus() {
      return this.status;
    }

    public String getStrategy() {
      return this.strategy;
    }

    public List<SerializePhase> getPhases() {
      return this.phases;
    }

    public int getTotalSteps() {
      return this.totalSteps;
    }

    public int getCompletedSteps() {
      return this.completedSteps;
    }
  }

  /**
   * This class is used to capture the reporting state of a service.
   */
  public static class SerializePlansTracker {

    private final SchedulerState schedulerState;

    private final List<String> activePlans;

    private final List<SerializePlan> plans;

    private final List<SerializeElement> serviceTopology;

    public SerializePlansTracker(SchedulerState schedulerState,
                                 List<String> activePlans,
                                 List<SerializePlan> serializePlans,
                                 List<SerializeElement> serviceTopology)
    {
      this.schedulerState = schedulerState;
      this.activePlans = activePlans;
      this.plans = serializePlans;
      this.serviceTopology = serviceTopology;
    }

    public SchedulerState getSchedulerState() {
      return this.schedulerState;
    }

    public List<String> getActivePlans() {
      return this.activePlans;
    }

    public List<SerializePlan> getPlans() {
      return this.plans;
    }

    public List<SerializeElement> getServiceTopology() {
      return this.serviceTopology;
    }
  }

  /**
   * This class is used to capture the basics of the {@link ParentElement} iterface.
   */
  public static class SerializeElement {

    private final String name;

    private final String type;

    private final Optional<List<SerializeElement>> children;

    public SerializeElement(String name, String type, Optional<List<SerializeElement>> children) {
      this.name = name;
      this.type = type;
      this.children = children;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public Optional<List<SerializeElement>> getChildren() {
      return children;
    }
  }
}
