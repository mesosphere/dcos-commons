package com.mesosphere.sdk.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.api.types.PlanInfo;
import com.mesosphere.sdk.offer.evaluate.placement.RegexMatcher;
import com.mesosphere.sdk.offer.evaluate.placement.StringMatcher;
import com.mesosphere.sdk.scheduler.plan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API for management of Plan(s).
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class PlansResource {
    static final Response ELEMENT_NOT_FOUND_RESPONSE = Response.status(Response.Status.NOT_FOUND)
            .entity("Element not found")
            .build();
    private static final StringMatcher ENVVAR_MATCHER = RegexMatcher.create("[A-Za-z_][A-Za-z0-9_]*");

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PlanCoordinator planCoordinator;

    public PlansResource(final PlanCoordinator planCoordinator) {
        this.planCoordinator = planCoordinator;
    }

    /**
     * Returns list of all configured plans.
     */
    @GET
    @Path("/plans")
    public Response getPlansInfo() {
        return Response
                .status(200)
                .entity(getPlanNames())
                .build();
    }

    /**
     * Returns a full list of the {@link Plan}'s contents (incl all {@link Phase}s/{@link Step}s).
     */
    @GET
    @Path("/plans/{planName}")
    public Response getPlanInfo(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            PlanManager planManager = planManagerOptional.get();
            return Response
                    .status(planManager.getPlan().isComplete() ? 200 : 503)
                    .entity(PlanInfo.forPlan(planManager.getPlan()))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
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
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: start"))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    /**
     * Idempotently stops a plan.  If a plan is in progress, it is interrupted and the plan is reset such that all
     * elements are pending.  If a plan is already stopped, it has no effect.
     */
    @POST
    @Path("/plans/{planName}/stop")
    public Response stopPlan(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            Plan plan = planManagerOptional.get().getPlan();
            plan.interrupt();
            plan.restart();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: stop"))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/continue")
    public Response continueCommand(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            planManagerOptional.get().getPlan().proceed();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: continue"))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/{phaseName}/continue")
    public Response continueCommand(@PathParam("planName") String planName,
                                     @PathParam("phaseName") String phaseName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            Plan plan = planManagerOptional.get().getPlan();
            List<Phase> phases = plan.getChildren().stream()
                    .filter(p -> p.getName().toString().equals(phaseName))
                    .collect(Collectors.toList());
            phases.forEach(p -> p.getStrategy().interrupt());
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: continue"))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/interrupt")
    public Response interruptCommand(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            planManagerOptional.get().getPlan().interrupt();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: interrupt"))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/{phaseName}/interrupt")
    public Response interruptCommand(@PathParam("planName") String planName,
                                     @PathParam("phaseName") String phaseName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            Plan plan = planManagerOptional.get().getPlan();
            List<Phase> phases = plan.getChildren().stream()
                    .filter(p -> p.getName().toString().equals(phaseName))
                    .collect(Collectors.toList());
            phases.forEach(p -> p.getStrategy().interrupt());
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: interrupt"))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/forceComplete")
    public Response forceCompleteCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phaseId,
            @QueryParam("step") String stepId) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            Optional<Step> step = getStep(planManagerOptional.get(), phaseId, stepId);
            if (step.isPresent()) {
                step.get().forceComplete();
                return Response.status(Response.Status.OK)
                        .entity(new CommandResultInfo("Received cmd: forceComplete"))
                        .build();
            } else {
                return ELEMENT_NOT_FOUND_RESPONSE;
            }
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/restart")
    public Response restartCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phaseId,
            @QueryParam("step") String stepId) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            if (phaseId == null && stepId == null) {
                Plan plan = planManagerOptional.get().getPlan();
                plan.restart();
                plan.proceed();
            } else {
                Optional<Step> step = getStep(planManagerOptional.get(), phaseId, stepId);
                if (step.isPresent()) {
                    step.get().restart();
                } else {
                    return ELEMENT_NOT_FOUND_RESPONSE;
                }
            }

            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: restart"))
                    .build();
        } else {
            return ELEMENT_NOT_FOUND_RESPONSE;
        }
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
        return continueCommand("deploy");
    }

    @POST
    @Deprecated
    @Path("/plan/interrupt")
    public Response interruptCommand() {
        return interruptCommand("deploy");
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

    private Optional<Step> getStep(PlanManager manager, String phaseId, String stepId) {
        List<Phase> phases = manager.getPlan().getChildren().stream()
                .filter(phase -> phase.getId().equals(UUID.fromString(phaseId)))
                .collect(Collectors.toList());

        if (phases.size() == 1) {
            List<Step> steps = phases.get(0).getChildren().stream()
                    .filter(step -> step.getId().equals(UUID.fromString(stepId)))
                    .collect(Collectors.toList());

            if (steps.size() == 1) {
                return steps.stream().findFirst();
            } else {
                logger.error("Expected 1 Step, found: " + steps);
                return Optional.empty();
            }
        } else {
            logger.error("Expected 1 Phase, found: " + phases);
            return Optional.empty();
        }
    }

    private List<String> getPlanNames() {
        return planCoordinator.getPlanManagers().stream()
                .map(planManager -> planManager.getPlan().getName())
                .collect(Collectors.toList());
    }

    private Optional<PlanManager> getPlanManager(String planName) {
        return planCoordinator.getPlanManagers().stream()
                .filter(planManager -> planManager.getPlan().getName().equals(planName))
                .findFirst();
    }

    private static Response invalidParameterResponse(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Couldn't parse parameters: " + message)
                .build();
    }

    private static void validate(Map<String, String> parameters) throws ValidationException {
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!ENVVAR_MATCHER.matches(entry.getKey())) {
                throw new ValidationException(
                        String.format("%s is not a valid environment variable name", entry.getKey()));
            }
        }
    }

    static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    static class CommandResultInfo {
        private final String msg;

        CommandResultInfo(String msg) {
            this.msg = msg;
        }

        @JsonProperty("message")
        public String getMessage() {
            return msg;
        }
    }
}
