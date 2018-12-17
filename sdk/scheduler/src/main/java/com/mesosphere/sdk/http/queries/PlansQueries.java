package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.StringMatcher;
import com.mesosphere.sdk.scheduler.plan.Element;
import com.mesosphere.sdk.scheduler.plan.Interruptible;
import com.mesosphere.sdk.scheduler.plan.ParentElement;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * API for management of Plan(s).
 */
public final class PlansQueries {

  private static final Logger LOGGER = LoggingUtils.getLogger(PlansQueries.class);

  private static final StringMatcher ENVVAR_MATCHER = RegexMatcher.create("[A-Za-z_][A-Za-z0-9_]*");

  private PlansQueries() {
    // do not instantiate
  }

  /**
   * Returns list of all configured plans.
   */
  public static Response list(Collection<PlanManager> planManagers) {
    return ResponseUtils.jsonOkResponse(new JSONArray(getPlanNames(planManagers)));
  }

  /**
   * Returns a full list of the {@link Plan}'s contents (incl all {@link Phase}s/{@link Step}s).
   */
  public static Response get(Collection<PlanManager> planManagers, String planName) {
    final Optional<PlanManager> planManagerOptional = getPlanManager(planManagers, planName);
    if (!planManagerOptional.isPresent()) {
      return planNotFoundResponse(planName);
    }

    Plan plan = planManagerOptional.get().getPlan();
    Response.Status response = Response.Status.ACCEPTED;
    if (plan.hasErrors()) {
      response = Response.Status.EXPECTATION_FAILED;
    } else if (plan.isComplete()) {
      response = Response.Status.OK;
    }
    return ResponseUtils.jsonResponseBean(PlanInfo.forPlan(plan), response);
  }

  /**
   * Idempotently starts a plan.  If a plan is complete, it restarts the plan.  If it is interrupted, in makes the
   * plan proceed.  If a plan is already in progress, it has no effect.
   */
  public static Response start(
      Collection<PlanManager> planManagers, String planName, Map<String, String> parameters)
  {
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if (!ENVVAR_MATCHER.matches(entry.getKey())) {
        return invalidParameterResponse(
            String.format("%s is not a valid environment variable name", entry.getKey()));
      }
    }

    final Optional<PlanManager> planManagerOptional = getPlanManager(planManagers, planName);
    if (!planManagerOptional.isPresent()) {
      return planNotFoundResponse(planName);
    }
    Plan plan = planManagerOptional.get().getPlan();
    plan.updateParameters(parameters);
    if (plan.isComplete()) {
      plan.restart();
    }

    plan.proceed();

    LOGGER.info("Started plan {} with parameters {} by user request", planName, parameters);

    return ResponseUtils.jsonOkResponse(getCommandResult(
        String.format("start %s with parameters: %s", planName, parameters.toString())
    ));
  }

  /**
   * Idempotently stops a plan.  If a plan is in progress, it is interrupted and the plan is reset such that all
   * elements are pending.  If a plan is already stopped, it has no effect.
   *
   * @see #interruptCommand(String, String) for the distinctions between Stop and Interrupt actions.
   */
  public static Response stop(Collection<PlanManager> planManagers, String planName) {
    final Optional<PlanManager> planManagerOptional = getPlanManager(planManagers, planName);
    if (!planManagerOptional.isPresent()) {
      return planNotFoundResponse(planName);
    }
    Plan plan = planManagerOptional.get().getPlan();
    plan.interrupt();
    plan.restart();
    return ResponseUtils.jsonOkResponse(getCommandResult("stop"));
  }

  public static Response continuePlan(
      Collection<PlanManager> planManagers,
      String planName,
      String phase)
  {
    final Optional<PlanManager> planManagerOptional = getPlanManager(planManagers, planName);
    if (!planManagerOptional.isPresent()) {
      return planNotFoundResponse(planName);
    }

    if (phase != null) {
      List<Phase> phases = getPhases(planManagerOptional.get(), phase);
      if (phases.isEmpty()) {
        return phaseNotFoundResponse(phase);
      }

      boolean allInProgress = phases.stream()
          .filter(Element::isRunning)
          .count() == phases.size();

      boolean allComplete = phases
          .stream()
          .filter(Element::isComplete)
          .count() == phases.size();

      if (allInProgress || allComplete) {
        return ResponseUtils.alreadyReportedResponse();
      }

      phases.forEach(ParentElement::proceed);
    } else {
      Plan plan = planManagerOptional.get().getPlan();
      if (plan.isRunning() || plan.isComplete()) {
        return ResponseUtils.alreadyReportedResponse();
      }
      plan.proceed();
    }

    return ResponseUtils.jsonOkResponse(getCommandResult("continue"));
  }

  /**
   * Interrupts (Pauses) a plan or if specified a phase within a plan.  If a plan/phase is in progress, it
   * is interrupted and the plan/phase is reset such that it is pending.  If the plan/phase is already in
   * a non-interruptable state (interrupted or complete), the response will indicate as such.
   * <p>
   * An interrupted Phase is not immediately halted, but sets the interrupted bit to ensure that subsequent
   * requests to process will not proceed. @see Interruptible .
   * <p>
   * Interrupt differs from stop in the following ways:
   * A) Interrupt can be issued for a specific phase or for all phases within a plan.  Stop can only be
   * issued for a plan.
   * B) Interrupt updates the underlying Phase/Step state. Stop not only updates the underlying state, but
   * also restarts the Plan.
   */
  public static Response interrupt(
      Collection<PlanManager> planManagers,
      String planName,
      String phase)
  {
    final Optional<PlanManager> planManagerOptional = getPlanManager(planManagers, planName);
    if (!planManagerOptional.isPresent()) {
      return planNotFoundResponse(planName);
    }

    if (phase != null) {
      List<Phase> phases = getPhases(planManagerOptional.get(), phase);
      if (phases.isEmpty()) {
        return phaseNotFoundResponse(phase);
      }

      boolean allInterrupted = phases.stream()
          .filter(ParentElement::isInterrupted).count() == phases.size();

      boolean allComplete = phases.stream()
          .filter(Element::isComplete).count() == phases.size();

      if (allInterrupted || allComplete) {
        return ResponseUtils.alreadyReportedResponse();
      }

      phases.forEach(p -> p.getStrategy().interrupt());
    } else {
      Plan plan = planManagerOptional.get().getPlan();
      if (plan.isInterrupted() || plan.isComplete()) {
        return ResponseUtils.alreadyReportedResponse();
      }
      plan.interrupt();
    }

    return ResponseUtils.jsonOkResponse(getCommandResult("interrupt"));
  }

  public static Response forceComplete(
      Collection<PlanManager> planManagers, String planName, String phaseName, String stepName)
  {
    ElementOrError elementOrError = getElementOrError(planManagers, planName, phaseName, stepName);
    if (elementOrError.error != null) {
      return elementOrError.error;
    }

    if (elementOrError.element.isComplete()) {
      return ResponseUtils.alreadyReportedResponse();
    }

    elementOrError.element.forceComplete();
    return ResponseUtils.jsonOkResponse(
        getPlanCommandResult("forceComplete", planName, phaseName, stepName)
    );
  }

  public static Response restart(
      Collection<PlanManager> planManagers, String planName, String phaseName, String stepName)
  {
    ElementOrError elementOrError = getElementOrError(planManagers, planName, phaseName, stepName);
    if (elementOrError.error != null) {
      return elementOrError.error;
    }

    if (elementOrError.element.isPending()) {
      return ResponseUtils.alreadyReportedResponse();
    }

    if (elementOrError.element instanceof Interruptible) {
      ((Interruptible) elementOrError.element).proceed();
    }

    elementOrError.element.restart();
    return ResponseUtils.jsonOkResponse(
        getPlanCommandResult("restart", planName, phaseName, stepName)
    );
  }

  private static ElementOrError getElementOrError(
      Collection<PlanManager> planManagers, String planName, String phaseName, String stepName)
  {
    final Optional<PlanManager> planManager = getPlanManager(planManagers, planName);
    if (!planManager.isPresent()) {
      return ElementOrError.error(planNotFoundResponse(planName));
    }

    // Plan is present, but no Phase or Step
    if (StringUtils.isBlank(phaseName) && StringUtils.isBlank(stepName)) {
      return ElementOrError.element(planManager.get().getPlan());
    }

    if (StringUtils.isBlank(phaseName)) {
      // Specified step but not phase
      return ElementOrError.error(
          ResponseUtils.plainResponse("Missing phase", Response.Status.BAD_REQUEST)
      );
    }

    Optional<Phase> phase = getPhases(planManager.get(), phaseName).stream().findFirst();
    if (!phase.isPresent()) {
      return ElementOrError.error(phaseNotFoundResponse(phaseName));
    }

    // Plan and Phase are present but no Step
    if (StringUtils.isBlank(stepName)) {
      return ElementOrError.element(phase.get());
    }

    // Plan and Phase and Step are present
    Optional<Step> step = getStep(Collections.singletonList(phase.get()), stepName);
    return step.isPresent()
        ? ElementOrError.element(step.get())
        : ElementOrError.error(stepNotFoundResponse(stepName));
  }

  private static List<Phase> getPhases(PlanManager manager, String phaseIdOrName) {
    try {
      UUID phaseId = UUID.fromString(phaseIdOrName);
      return manager.getPlan().getChildren().stream()
          .filter(phase -> phase.getId().equals(phaseId))
          .collect(Collectors.toList());
    } catch (IllegalArgumentException e) {
      // couldn't parse as UUID: fall back to treating phase identifier as a name
      return manager.getPlan().getChildren().stream()
          .filter(phase -> phase.getName().equals(phaseIdOrName))
          .collect(Collectors.toList());
    }
  }

  private static Optional<Step> getStep(List<Phase> phases, String stepIdOrName) {
    List<Step> steps;
    try {
      UUID stepId = UUID.fromString(stepIdOrName);
      steps = phases.stream().map(ParentElement::getChildren)
          .flatMap(List::stream)
          .filter(step -> step.getId().equals(stepId))
          .collect(Collectors.toList());
    } catch (IllegalArgumentException e) {
      // couldn't parse as UUID: fall back to treating step identifier as a name
      steps = phases.stream().map(ParentElement::getChildren)
          .flatMap(List::stream)
          .filter(step -> step.getName().equals(stepIdOrName))
          .collect(Collectors.toList());
    }
    if (steps.size() == 1) {
      return Optional.of(steps.get(0));
    } else {
      LOGGER.error(
          "Expected 1 step '{}' across {} phases, got: {}", stepIdOrName, phases.size(), steps);
      return Optional.empty();
    }
  }

  private static List<String> getPlanNames(Collection<PlanManager> planManagers) {
    return planManagers.stream()
        .map(planManager -> planManager.getPlan().getName())
        .collect(Collectors.toList());
  }

  private static Optional<PlanManager> getPlanManager(
      Collection<PlanManager> planManagers,
      String planName)
  {
    return planManagers.stream()
        .filter(planManager -> planManager.getPlan().getName().equals(planName))
        .findFirst();
  }

  private static Response invalidParameterResponse(String message) {
    return ResponseUtils.plainResponse(
        String.format("Couldn't parse parameters: %s", message),
        Response.Status.BAD_REQUEST);
  }

  private static Response planNotFoundResponse(String plan) {
    return ResponseUtils.notFoundResponse(String.format("Plan %s", plan));
  }

  private static Response phaseNotFoundResponse(String phase) {
    return ResponseUtils.notFoundResponse(String.format("Phase %s", phase));
  }

  private static Response stepNotFoundResponse(String step) {
    return ResponseUtils.notFoundResponse(String.format("Step %s", step));
  }

  private static JSONObject getCommandResult(String command) {
    return new JSONObject(Collections.singletonMap(
        "message",
        String.format("Received cmd: %s", command)));
  }

  private static JSONObject getPlanCommandResult(
      String cmd,
      String plan,
      String phase,
      String step)
  {
    return getCommandResult(
        String.format("%s for Plan: %s, Phase: %s, Step: %s", cmd, plan, phase, step)
    );
  }

  private static class ElementOrError {
    Element element;

    Response error;

    private static ElementOrError element(Element element) {
      ElementOrError e = new ElementOrError();
      e.element = element;
      return e;
    }

    private static ElementOrError error(Response error) {
      ElementOrError e = new ElementOrError();
      e.error = error;
      return e;
    }
  }
}
