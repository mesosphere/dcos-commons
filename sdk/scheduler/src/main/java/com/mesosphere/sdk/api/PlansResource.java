package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.types.PlanInfo;
import com.mesosphere.sdk.api.types.PrettyJsonResource;
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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.api.ResponseUtils.*;

/**
 * API for management of Plan(s).
 */
@Path("/v1")
public class PlansResource extends PrettyJsonResource {

    private static final StringMatcher ENVVAR_MATCHER = RegexMatcher.create("[A-Za-z_][A-Za-z0-9_]*");

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Collection<PlanManager> planManagers = new ArrayList<>();
    private final Object planManagersLock = new Object();

    /**
     * Assigns the list of plans to be managed via this endpoint.
     *
     * @return this
     */
    public PlansResource setPlanManagers(Collection<PlanManager> planManagers) {
        synchronized (planManagersLock) {
            this.planManagers.clear();
            this.planManagers.addAll(planManagers);
        }
        return this;
    }

    /**
     * Returns list of all configured plans.
     */
    @GET
    @Path("/plans")
    public Response listPlans() {
        return jsonOkResponse(new JSONArray(getPlanNames()));
    }

    /**
     * Returns a full list of the {@link Plan}'s contents (incl all {@link Phase}s/{@link Step}s).
     */
    @GET
    @Path("/plans/{planName}")
    public Response getPlanInfo(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (!planManagerOptional.isPresent()) {
            return elementNotFoundResponse();
        }

        Plan plan = planManagerOptional.get().getPlan();
        Response.Status response = Response.Status.ACCEPTED;
        if (plan.hasErrors()) {
            response = Response.Status.EXPECTATION_FAILED;
        } else if (plan.isComplete()) {
            response = Response.Status.OK;
        }
        return jsonResponseBean(PlanInfo.forPlan(plan), response);
    }

    /**
     * Idempotently starts a plan.  If a plan is complete, it restarts the plan.  If it is interrupted, in makes the
     * plan proceed.  If a plan is already in progress, it has no effect.
     */
    @POST
    @Path("/plans/{planName}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response startPlan(@PathParam("planName") String planName, Map<String, String> parameters) {
        try {
            validate(parameters);
        } catch (ValidationException e) {
            return invalidParameterResponse(e.getMessage());
        }

        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            Plan plan = planManagerOptional.get().getPlan();
            plan.updateParameters(parameters);
            if (plan.isComplete()) {
                plan.restart();
            }

            plan.proceed();

            logger.info("Started plan {} with parameters {} by user request", planName, parameters);

            return jsonOkResponse(getCommandResult(String.format("%s %s with parameters: %s",
                    "start",
                    planName,
                    parameters.toString())));
        } else {
            return elementNotFoundResponse();
        }
    }

    /**
     * Idempotently stops a plan.  If a plan is in progress, it is interrupted and the plan is reset such that all
     * elements are pending.  If a plan is already stopped, it has no effect.
     *
     * @see #interruptCommand(String, String) for the distinctions between Stop and Interrupt actions.
     */
    @POST
    @Path("/plans/{planName}/stop")
    public Response stopPlan(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (!planManagerOptional.isPresent()) {
            return elementNotFoundResponse();
        }
        Plan plan = planManagerOptional.get().getPlan();
        plan.interrupt();
        plan.restart();
        return jsonOkResponse(getCommandResult("stop"));
    }

    @POST
    @Path("/plans/{planName}/continue")
    public Response continueCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (!planManagerOptional.isPresent()) {
            return elementNotFoundResponse();
        }

        if (phase != null) {
            List<Phase> phases = getPhases(planManagerOptional.get(), phase);
            if (phases.isEmpty()) {
                return elementNotFoundResponse();
            }

            boolean allInProgress = phases.stream()
                    .filter(phz -> phz.isRunning())
                    .count() == phases.size();

            boolean allComplete = phases.stream()
                    .filter(phz -> phz.isComplete()).count() == phases.size();

            if (allInProgress || allComplete) {
                return alreadyReportedResponse();
            }

            phases.forEach(ParentElement::proceed);
        } else {
            Plan plan = planManagerOptional.get().getPlan();
            if (plan.isRunning() || plan.isComplete()) {
                return alreadyReportedResponse();
            }
            plan.proceed();
        }

        return jsonOkResponse(getCommandResult("continue"));
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
    @POST
    @Path("/plans/{planName}/interrupt")
    public Response interruptCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (!planManagerOptional.isPresent()) {
            return elementNotFoundResponse();
        }

        if (phase != null) {
            List<Phase> phases = getPhases(planManagerOptional.get(), phase);
            if (phases.isEmpty()) {
                return elementNotFoundResponse();
            }

            boolean allInterrupted = phases.stream()
                    .filter(phz -> phz.isInterrupted()).count() == phases.size();

            boolean allComplete = phases.stream()
                    .filter(phz -> phz.isComplete()).count() == phases.size();

            if (allInterrupted || allComplete) {
                return alreadyReportedResponse();
            }

            phases.forEach(p -> p.getStrategy().interrupt());
        } else {
            Plan plan = planManagerOptional.get().getPlan();
            if (plan.isInterrupted() || plan.isComplete()) {
                return alreadyReportedResponse();
            }
            plan.interrupt();
        }

        return jsonOkResponse(getCommandResult("interrupt"));
    }

    @POST
    @Path("/plans/{planName}/forceComplete")
    public Response forceCompleteCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (!planManagerOptional.isPresent()) {
            return elementNotFoundResponse();
        }

        Optional<Step> stepOptional = getStep(getPhases(planManagerOptional.get(), phase), step);
        if (!stepOptional.isPresent()) {
            return elementNotFoundResponse();
        } else if (stepOptional.get().isComplete()) {
            return alreadyReportedResponse();
        }

        stepOptional.get().forceComplete();

        return jsonOkResponse(getCommandResult("forceComplete"));
    }

    @POST
    @Path("/plans/{planName}/restart")
    public Response restartCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (!planManagerOptional.isPresent()) {
            return elementNotFoundResponse();
        }

        if (phase == null && step == null) {
            Plan plan = planManagerOptional.get().getPlan();
            plan.restart();
            plan.proceed();
            return jsonOkResponse(getCommandResult("restart"));
        }

        if (phase != null && step == null) {
            List<Phase> phases = getPhases(planManagerOptional.get(), phase);
            if (phases.isEmpty()) {
                return elementNotFoundResponse();
            }

            phases.forEach(phz -> phz.restart());
            phases.forEach(phz -> phz.proceed());
            return jsonOkResponse(getCommandResult("restart"));
        }

        if (phase != null && step != null) {
            Optional<Step> stepOptional = getStep(getPhases(planManagerOptional.get(), phase), step);
            if (!stepOptional.isPresent()) {
                return elementNotFoundResponse();
            }
            stepOptional.get().restart();
            stepOptional.get().proceed();
            return jsonOkResponse(getCommandResult("restart"));
        }

        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @GET
    @Deprecated
    @Path("/plan")
    public Response getFullInfo() {
        return getPlanInfo("deploy");
    }

    @POST
    @Deprecated
    @Path("/plan/continue")
    public Response continueCommand() {
        return continueCommand("deploy", null);
    }

    @POST
    @Deprecated
    @Path("/plan/interrupt")
    public Response interruptCommand() {
        return interruptCommand("deploy", null);
    }

    @POST
    @Deprecated
    @Path("/plan/forceComplete")
    public Response forceCompleteCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("step") String stepId) {
        return forceCompleteCommand("deploy", phaseId, stepId);
    }

    @POST
    @Deprecated
    @Path("/plan/restart")
    public Response restartCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("step") String stepId) {
        return restartCommand("deploy", phaseId, stepId);
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

    private Optional<Step> getStep(List<Phase> phases, String stepIdOrName) {
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
            logger.error("Expected 1 step '{}' across {} phases, got: {}", stepIdOrName, phases.size(), steps);
            return Optional.empty();
        }
    }

    private List<String> getPlanNames() {
        synchronized (planManagersLock) {
            return planManagers.stream()
                    .map(planManager -> planManager.getPlan().getName())
                    .collect(Collectors.toList());
        }
    }

    private Optional<PlanManager> getPlanManager(String planName) {
        synchronized (planManagersLock) {
            if (planManagers.isEmpty()) {
                logger.error("Plan managers haven't been initialized yet. Unable to retrieve plan {}", planName);
            }
            return planManagers.stream()
                    .filter(planManager -> planManager.getPlan().getName().equals(planName))
                    .findFirst();
        }
    }

    private static Response invalidParameterResponse(String message) {
        return plainResponse(
                String.format("Couldn't parse parameters: %s", message),
                Response.Status.BAD_REQUEST);
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
