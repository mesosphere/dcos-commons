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

    private Map<String, PlanManager> planManagers = new HashMap<>();

    public PlansResource(final Map<String, PlanManager> planManagers) {
        this.planManagers.putAll(planManagers);
    }

    /**
     * Returns list of all configured plans.
     */
    @GET
    @Path("/plans")
    public Response getPlansInfo() {
        return Response
                .status(200)
                .entity(planManagers.keySet())
                .build();
    }

    /**
     * Returns a full list of the {@link Plan}'s contents (incl all {@link Phase}s/{@link Step}s).
     */
    @GET
    @Path("/plans/{planName}")
    public Response getPlanInfo(@PathParam("planName") String planName) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            return Response
                    .status(manager.getPlan().isComplete() ? 200 : 503)
                    .entity(PlanInfo.forPlan(manager))
                    .build();
        } else {
            return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/plans/{planName}/continue")
    public Response continueCommand(@PathParam("planName") String planName) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            manager.getPlan().getStrategy().proceed();
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
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            manager.getPlan().getStrategy().interrupt();
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
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            Optional<Step> step = getStep(manager, phaseId, stepId);
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
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            Optional<Step> step = getStep(manager, phaseId, stepId);
            if (step.isPresent()) {
                step.get().restart();
                return Response.status(Response.Status.OK)
                        .entity(new CommandResultInfo("Received cmd: restart"))
                        .build();
            } else {

                return PLAN_ELEMENT_NOT_FOUND_RESPONSE;
            }
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
