package org.apache.mesos.scheduler.plan.api;

import org.apache.mesos.scheduler.plan.PlanManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API for management of Plan(s).
 */
@Path("/v1/plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlansResource {
    private static final Response PLAN_NOT_FOUND_RESPONSE = Response.status(Response.Status.NOT_FOUND)
            .entity("Plan not found")
            .build();

    private Map<String, PlanManager> planManagers = new HashMap<>();

    public PlansResource(final Map<String, PlanManager> planManagers) {
        this.planManagers.putAll(planManagers);
    }

    /**
     * Returns the status of the currently active Phase/Block.
     */
    @GET
    @Path("/{planName}/status")
    public Response getStatus(@PathParam("planName") String planName) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            return Response.status(Response.Status.OK)
                    .entity(CurrentlyActiveInfo.forPlan(manager))
                    .build();
        } else {
            return PLAN_NOT_FOUND_RESPONSE;
        }
    }

    /**
     * Returns list of all configured plans.
     */
    @GET
    public Response getPlansInfo() {
        return Response
                .status(200)
                .entity(planManagers.keySet())
                .build();
    }

    /**
     * Returns a full list of the Plan's contents (incl all Phases/Blocks).
     */
    @GET
    @Path("/{planName}")
    public Response getPlanInfo(@PathParam("planName") String planName) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            return Response
                    .status(manager.isComplete() ? 200 : 503)
                    .entity(StageInfo.forStage(manager))
                    .build();
        } else {
            return PLAN_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/{planName}/continue")
    public Response continueCommand(@PathParam("planName") String planName) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            manager.proceed();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: continue"))
                    .build();
        } else {
            return PLAN_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/{planName}/interrupt")
    public Response interruptCommand(@PathParam("planName") String planName) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            manager.interrupt();
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: interrupt"))
                    .build();
        } else {
            return PLAN_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/{planName}/forceComplete")
    public Response forceCompleteCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phaseId,
            @QueryParam("block") String blockId) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            manager.forceComplete(UUID.fromString(phaseId), UUID.fromString(blockId));
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: forceComplete"))
                    .build();
        } else {
            return PLAN_NOT_FOUND_RESPONSE;
        }
    }

    @POST
    @Path("/{planName}/restart")
    public Response restartCommand(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phaseId,
            @QueryParam("block") String blockId) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            manager.restart(UUID.fromString(phaseId), UUID.fromString(blockId));
            return Response.status(Response.Status.OK)
                    .entity(new CommandResultInfo("Received cmd: restart"))
                    .build();
        } else {
            return PLAN_NOT_FOUND_RESPONSE;
        }
    }
}
