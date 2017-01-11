package com.mesosphere.sdk.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.api.types.PlanInfo;
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
@Consumes(MediaType.APPLICATION_JSON)
public class PlansResource {
    static final Response PLAN_ELEMENT_NOT_FOUND_RESPONSE = Response.status(Response.Status.NOT_FOUND)
            .entity("Element not found")
            .build();

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
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    /**
     * Idempotently starts a plan.  If a plan is complete, it restarts the plan.  If it is interrupted, in makes the
     * plan proceed.  If a plan is already in progress, it has no effect.
     */
    @POST
    @Path("/plans/{planName}/start")
    public Response startPlan(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            Plan plan = planManagerOptional.get().getPlan();
            if (plan.isComplete()) {
                plan.restart();
            }

            plan.getStrategy().proceed();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: start"))
                    .build();
        } else {
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
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
            plan.getStrategy().interrupt();
            plan.restart();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: stop"))
                    .build();
        } else {
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/continue")
    public Response continueCommand(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            planManagerOptional.get().getPlan().getStrategy().proceed();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: continue"))
                    .build();
        } else {
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/interrupt")
    public Response interruptCommand(@PathParam("planName") String planName) {
        final Optional<PlanManager> planManagerOptional = getPlanManager(planName);
        if (planManagerOptional.isPresent()) {
            planManagerOptional.get().getPlan().getStrategy().interrupt();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: interrupt"))
                    .build();
        } else {
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
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
                return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
            }
        } else {
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
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
                plan.getStrategy().proceed();
            } else {
                Optional<Step> step = getStep(planManagerOptional.get(), phaseId, stepId);
                if (step.isPresent()) {
                    step.get().restart();
                } else {
                    return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
                }
            }

            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: restart"))
                    .build();
        } else {
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
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
            Element<Step> phase = phases.stream().findFirst().get();

            List<Step> steps = phase.getChildren().stream()
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
