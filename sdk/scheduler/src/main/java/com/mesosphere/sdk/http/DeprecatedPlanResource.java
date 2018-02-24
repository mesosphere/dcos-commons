package com.mesosphere.sdk.http;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * Implements a set of deprecated /plan/ commands which operate against the Deploy plan.
 */
@Path("plan")
public class DeprecatedPlanResource {

    private static final String PLAN = "deploy";

    private final PlansResource plans;

    public DeprecatedPlanResource(PlansResource plans) {
        this.plans = plans;
    }

    @GET
    @Deprecated
    @Path("")
    public Response getFullInfo() {
        return plans.getPlanInfo(PLAN);
    }

    @POST
    @Deprecated
    @Path("continue")
    public Response continueCommand() {
        return plans.continueCommand(PLAN, null);
    }

    @POST
    @Deprecated
    @Path("interrupt")
    public Response interruptCommand() {
        return plans.interruptCommand(PLAN, null);
    }

    @POST
    @Deprecated
    @Path("forceComplete")
    public Response forceCompleteCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("step") String stepId) {
        return plans.forceCompleteCommand(PLAN, phaseId, stepId);
    }

    @POST
    @Deprecated
    @Path("restart")
    public Response restartCommand(
            @QueryParam("phase") String phaseId,
            @QueryParam("step") String stepId) {
        return plans.restartCommand(PLAN, phaseId, stepId);
    }
}
