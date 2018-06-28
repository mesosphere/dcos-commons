package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.queries.PlansQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.Map;

/**
 * API for management of Plan(s).
 */
@Singleton
@Path("/v1/plans")
public class PlansResource extends PrettyJsonResource {

    private final Collection<PlanManager> planManagers;

    /**
     * Creates a new instance which allows access to plans in the provided coordinator.
     */
    public PlansResource(PlanCoordinator planCoordinator) {
        this(planCoordinator.getPlanManagers());
    }

    /**
     * Creates a new instance which allows access to the provided plans.
     */
    public PlansResource(Collection<PlanManager> planManagers) {
        this.planManagers = planManagers;
    }

    /**
     * @see PlansQueries
     */
    @GET
    public Response list() {
        return PlansQueries.list(planManagers);
    }

    /**
     * @see PlansQueries
     */
    @GET
    @Path("{planName}")
    public Response get(@PathParam("planName") String planName) {
        return PlansQueries.get(planManagers, planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{planName}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(@PathParam("planName") String planName, Map<String, String> parameters) {
        return PlansQueries.start(planManagers, planName, parameters);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{planName}/stop")
    public Response stop(@PathParam("planName") String planName) {
        return PlansQueries.stop(planManagers, planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{planName}/continue")
    public Response continuePlan(@PathParam("planName") String planName, @QueryParam("phase") String phase) {
        return PlansQueries.continuePlan(planManagers, planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{planName}/interrupt")
    public Response interrupt(@PathParam("planName") String planName, @QueryParam("phase") String phase) {
        return PlansQueries.interrupt(planManagers, planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{planName}/forceComplete")
    public Response forceComplete(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phaseName,
            @QueryParam("step") String stepName) {
        return PlansQueries.forceComplete(planManagers, planName, phaseName, stepName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{planName}/restart")
    public Response restart(
            @PathParam("planName") String planName,
            @QueryParam("phase") String phaseName,
            @QueryParam("step") String stepName) {
        return PlansQueries.restart(planManagers, planName, phaseName, stepName);
    }
}
