package org.apache.mesos.scheduler.plan.api;

import com.google.inject.Inject;
import org.apache.mesos.scheduler.plan.StageManager;

import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * API for stage display and control.
 */
@Path("/v1/plan")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StageResource {
    private final StageManager manager;

    @Inject
    public StageResource(final StageManager manager){
        this.manager = manager;
    }

    /**
     * Returns the status of the currently active Phase/Block.
     */
    @GET
    @Path("/status")
    public CurrentlyActiveInfo getStatus(){
        return CurrentlyActiveInfo.forStage(manager);
    }

    /**
     * Returns a full list of the Stage's contents (incl all Phases/Blocks).
     */
    @GET
    public Response getFullInfo(){
        int statusCode = 200;
        if (!manager.isComplete()) {
            statusCode = 503;
        }
        return Response
                .status(statusCode)
                .entity(StageInfo.forStage(manager))
                .build();
    }

    @POST
    @Path("/continue")
    public CommandResultInfo continueCommand(){
        manager.proceed();
        return new CommandResultInfo("Received cmd: continue");
    }

    @POST
    @Path("/interrupt")
    public CommandResultInfo interruptCommand(){
        manager.interrupt();
        return new CommandResultInfo("Received cmd: interrupt");
    }

    @POST
    @Path("/forceComplete")
    public CommandResultInfo forceCompleteCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("block") String blockId){
        manager.forceComplete(UUID.fromString(phaseId), UUID.fromString(blockId));
        return new CommandResultInfo("Received cmd: forceComplete");
    }

    @POST
    @Path("/restart")
    public CommandResultInfo restartCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("block") String blockId){
        manager.restart(UUID.fromString(phaseId), UUID.fromString(blockId));
        return new CommandResultInfo("Received cmd: restart");
    }
}
