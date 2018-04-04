package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.PlansQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;

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
    @Path("{sanitizedServiceName}/plans")
    @GET
    public Response list(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.list(planCoordinator.get().getPlanManagers());
    }

    /**
     * @see PlansQueries
     */
    @GET
    @Path("{sanitizedServiceName}/plans/{planName}")
    public Response get(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("planName") String planName) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.get(planCoordinator.get().getPlanManagers(), planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{sanitizedServiceName}/plans/{planName}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("planName") String planName,
            Map<String, String> parameters) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.start(planCoordinator.get().getPlanManagers(), planName, parameters);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{sanitizedServiceName}/plans/{planName}/stop")
    public Response stop(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("planName") String planName) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.stop(planCoordinator.get().getPlanManagers(), planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{sanitizedServiceName}/plans/{planName}/continue")
    public Response continuePlan(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.continuePlan(planCoordinator.get().getPlanManagers(), planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{sanitizedServiceName}/plans/{planName}/interrupt")
    public Response interrupt(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.interrupt(planCoordinator.get().getPlanManagers(), planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{sanitizedServiceName}/plans/{planName}/forceComplete")
    public Response forceComplete(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.forceComplete(planCoordinator.get().getPlanManagers(), planName, phase, step);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{sanitizedServiceName}/plans/{planName}/restart")
    public Response restart(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        Optional<PlanCoordinator> planCoordinator = getPlanCoordinator(sanitizedServiceName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PlansQueries.restart(planCoordinator.get().getPlanManagers(), planName, phase, step);
    }

    private Optional<PlanCoordinator> getPlanCoordinator(String sanitizedServiceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getServiceSanitized(sanitizedServiceName);
        return service.isPresent() ? Optional.of(service.get().getPlanCoordinator()) : Optional.empty();
    }
}
