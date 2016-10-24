package org.apache.mesos.scheduler.plan.api;

import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Element;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PlanManager;
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
     * Returns a full list of the Plan's contents (incl all Phases/Blocks).
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
            @QueryParam("block") String blockId) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            Optional<Block> block = getBlock(manager, phaseId, blockId);
            if (block.isPresent()) {
                block.get().forceComplete();
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
            @QueryParam("block") String blockId) {
        final PlanManager manager = planManagers.get(planName);
        if (manager != null) {
            Optional<Block> block = getBlock(manager, phaseId, blockId);
            if (block.isPresent()) {
                block.get().restart();
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
            @QueryParam("block") String blockId) {

        return forceCompleteCommand("deploy", phaseId, blockId);
    }

    @POST
    @Deprecated
    @Path("/plan/restart")
    public Response restartCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("block") String blockId) {
        return restartCommand("deploy", phaseId, blockId);
    }

    private Optional<Block> getBlock(PlanManager manager, String phaseId, String blockId) {
        List<Phase> phases = manager.getPlan().getChildren().stream()
                .filter(phase -> phase.getId().equals(UUID.fromString(phaseId)))
                .collect(Collectors.toList());

        if (phases.size() == 1) {
            Element<Block> phase = phases.stream().findFirst().get();

            List<Block> blocks = phase.getChildren().stream()
                    .filter(block -> block.getId().equals(UUID.fromString(blockId)))
                    .collect(Collectors.toList());

            if (blocks.size() == 1) {
                return blocks.stream().findFirst();
            } else {
                logger.error("Expected 1 Block, found: " + blocks);
                return Optional.empty();
            }
        } else {
            logger.error("Expected 1 Phase, found: " + phases);
            return Optional.empty();
        }
    }
}
