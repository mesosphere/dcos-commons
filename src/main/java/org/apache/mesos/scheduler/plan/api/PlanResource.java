package org.apache.mesos.scheduler.plan.api;

import com.google.inject.Inject;
import org.apache.mesos.scheduler.plan.PlanManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

/**
 * API for plan display and control.
 */
@Path("/v1/plan")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlanResource {
    private final PlanManager manager;

    @Inject
    public PlanResource(final PlanManager manager) {
        this.manager = manager;
    }

    /**
     * Returns the status of the currently active Phase/Block.
     */
    @GET
    @Path("/status")
    public CurrentlyActiveInfo getStatus() {
        return CurrentlyActiveInfo.forStage(manager);
    }

    /**
     * Returns a full list of the Plan's contents (incl all Phases/Blocks).
     */
    @GET
    public Response getFullInfo() {
        return Response
                .status(manager.isComplete() ? 200 : 503)
                .entity(StageInfo.forStage(manager))
                .build();
    }

    @POST
    @Path("/continue")
    public CommandResultInfo continueCommand() {
        manager.proceed();
        return new CommandResultInfo("Received cmd: continue");
    }

    @POST
    @Path("/interrupt")
    public CommandResultInfo interruptCommand() {
        manager.interrupt();
        return new CommandResultInfo("Received cmd: interrupt");
    }

    @POST
    @Path("/forceComplete")
    public CommandResultInfo forceCompleteCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("block") String blockId) {
        manager.forceComplete(UUID.fromString(phaseId), UUID.fromString(blockId));
        return new CommandResultInfo("Received cmd: forceComplete");
    }

    @POST
    @Path("/restart")
    public CommandResultInfo restartCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("block") String blockId) {
        manager.restart(UUID.fromString(phaseId), UUID.fromString(blockId));
        return new CommandResultInfo("Received cmd: restart");
    }
}
