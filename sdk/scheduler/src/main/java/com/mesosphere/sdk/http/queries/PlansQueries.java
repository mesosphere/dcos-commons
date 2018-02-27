package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.StringMatcher;
import com.mesosphere.sdk.scheduler.plan.ParentElement;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class PlansQueries extends PrettyJsonResource {

    private static final StringMatcher ENVVAR_MATCHER = RegexMatcher.create("[A-Za-z_][A-Za-z0-9_]*");

    private static final Logger LOGGER = LoggerFactory.getLogger(PlansQueries.class);

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
            Collection<PlanManager> planManagers, String planName, Map<String, String> parameters) {
        try {
            validate(parameters);
        } catch (ValidationException e) {
            return invalidParameterResponse(e.getMessage());
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

        return ResponseUtils.jsonOkResponse(getCommandResult(String.format("start %s with parameters: %s",
                planName, parameters.toString())));
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

    public static Response continuePlan(Collection<PlanManager> planManagers, String planName, String phase) {
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
                    .filter(phz -> phz.isRunning())
                    .count() == phases.size();

            boolean allComplete = phases.stream()
                    .filter(phz -> phz.isComplete()).count() == phases.size();

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
     *
     * An interrupted Phase is not immediately halted, but sets the interrupted bit to ensure that subsequent
     * requests to process will not proceed. @see Interruptible .
     *
     * Interrupt differs from stop in the following ways:
     * A) Interrupt can be issued for a specific phase or for all phases within a plan.  Stop can only be
     *    issued for a plan.
     * B) Interrupt updates the underlying Phase/Step state. Stop not only updates the underlying state, but
     *    also restarts the Plan.
     */
    public static Response interrupt(Collection<PlanManager> planManagers, String planName, String phase) {
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
                    .filter(phz -> phz.isInterrupted()).count() == phases.size();

            boolean allComplete = phases.stream()
                    .filter(phz -> phz.isComplete()).count() == phases.size();

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
            Collection<PlanManager> planManagers, String planName, String phase, String step) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planManagers, planName);
        if (!planManagerOptional.isPresent()) {
            return planNotFoundResponse(planName);
        }

        List<Phase> phases = getPhases(planManagerOptional.get(), phase);
        if (phases.isEmpty()) {
            return phaseNotFoundResponse(phase);

        }
        Optional<Step> stepOptional = getStep(phases, step);
        if (!stepOptional.isPresent()) {
            return stepNotFoundResponse(step);
        } else if (stepOptional.get().isComplete()) {
            return ResponseUtils.alreadyReportedResponse();
        }

        stepOptional.get().forceComplete();

        return ResponseUtils.jsonOkResponse(getCommandResult("forceComplete"));
    }

    public static Response restart(
            Collection<PlanManager> planManagers, String planName, String phase, String step) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planManagers, planName);
        if (!planManagerOptional.isPresent()) {
            return planNotFoundResponse(planName);
        }

        if (phase == null && step == null) {
            Plan plan = planManagerOptional.get().getPlan();
            plan.restart();
            plan.proceed();
            return ResponseUtils.jsonOkResponse(getCommandResult("restart"));
        }

        if (phase != null && step == null) {
            List<Phase> phases = getPhases(planManagerOptional.get(), phase);
            if (phases.isEmpty()) {
                return phaseNotFoundResponse(phase);
            }

            phases.forEach(phz -> phz.restart());
            phases.forEach(phz -> phz.proceed());
            return ResponseUtils.jsonOkResponse(getCommandResult("restart"));
        }

        if (phase != null && step != null) {
            Optional<Step> stepOptional = getStep(getPhases(planManagerOptional.get(), phase), step);
            if (!stepOptional.isPresent()) {
                return stepNotFoundResponse(step);
            }
            stepOptional.get().restart();
            stepOptional.get().proceed();
            return ResponseUtils.jsonOkResponse(getCommandResult("restart"));
        }

        return Response.status(Response.Status.BAD_REQUEST).build();
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
            LOGGER.error("Expected 1 step '{}' across {} phases, got: {}", stepIdOrName, phases.size(), steps);
            return Optional.empty();
        }
    }

    private static List<String> getPlanNames(Collection<PlanManager> planManagers) {
        return planManagers.stream()
                .map(planManager -> planManager.getPlan().getName())
                .collect(Collectors.toList());
    }

    private static Optional<PlanManager> getPlanManager(Collection<PlanManager> planManagers, String planName) {
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
        return ResponseUtils.notFoundResponse("Plan " + plan);
    }

    private static Response phaseNotFoundResponse(String phase) {
        return ResponseUtils.notFoundResponse("Phase " + phase);
    }

    private static Response stepNotFoundResponse(String step) {
        return ResponseUtils.notFoundResponse("Step " + step);
    }

    private static void validate(Map<String, String> parameters) throws ValidationException {
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!ENVVAR_MATCHER.matches(entry.getKey())) {
                throw new ValidationException(
                        String.format("%s is not a valid environment variable name", entry.getKey()));
            }
        }
    }

    private static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    private static JSONObject getCommandResult(String command) {
        return new JSONObject(Collections.singletonMap(
                "message",
                String.format("Received cmd: %s", command)));
    }
}
