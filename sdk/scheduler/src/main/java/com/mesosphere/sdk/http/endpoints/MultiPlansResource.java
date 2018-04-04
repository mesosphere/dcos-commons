package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.PlansQueries;
import com.mesosphere.sdk.http.types.MultiServiceManager;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.Optional;

/**
 * API for management of Plan(s).
 */
@Path("/v1/service")
public class MultiPlansResource extends PrettyJsonResource {

    private final MultiServiceManager multiServiceManager;

    /**
     * Creates a new instance which allows access to plans for runs in the provider.
     */
    public MultiPlansResource(MultiServiceManager multiServiceManager) {
        this.multiServiceManager = multiServiceManager;
    }

    /**
     * @see PlansQueries
     */
    @Path("{serviceName}/plans")
    @GET
    public Response list(@PathParam("serviceName") String serviceName) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.list(planCoordinator.get().getPlanManagers());
    }

    /**
     * @see PlansQueries
     */
    @GET
    @Path("{serviceName}/plans/{planName}")
    public Response get(@PathParam("serviceName") String serviceName, @PathParam("planName") String planName) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.get(planCoordinator.get().getPlanManagers(), planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{serviceName}/plans/{planName}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(
            @PathParam("serviceName") String serviceName,
            @PathParam("planName") String planName,
            Map<String, String> parameters) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.start(planCoordinator.get().getPlanManagers(), planName, parameters);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{serviceName}/plans/{planName}/stop")
    public Response stop(@PathParam("serviceName") String serviceName, @PathParam("planName") String planName) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.stop(planCoordinator.get().getPlanManagers(), planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{serviceName}/plans/{planName}/continue")
    public Response continuePlan(
            @PathParam("serviceName") String serviceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.continuePlan(planCoordinator.get().getPlanManagers(), planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{serviceName}/plans/{planName}/interrupt")
    public Response interrupt(
            @PathParam("serviceName") String serviceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.interrupt(planCoordinator.get().getPlanManagers(), planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{serviceName}/plans/{planName}/forceComplete")
    public Response forceComplete(
            @PathParam("serviceName") String serviceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.forceComplete(planCoordinator.get().getPlanManagers(), planName, phase, step);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{serviceName}/plans/{planName}/restart")
    public Response restart(
            @PathParam("serviceName") String serviceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(serviceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PlansQueries.restart(planCoordinator.get().getPlanManagers(), planName, phase, step);
    }

    private Optional<PlanCoordinator> getPlanCoordinator(String serviceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
        return service.isPresent() ? Optional.of(service.get().getPlanCoordinator()) : Optional.empty();
    }
}
