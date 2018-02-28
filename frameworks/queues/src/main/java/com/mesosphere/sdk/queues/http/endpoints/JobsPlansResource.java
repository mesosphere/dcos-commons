package com.mesosphere.sdk.queues.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.PlansQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.queues.http.types.JobInfoProvider;
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
@Path("/v1/jobs")
public class JobsPlansResource extends PrettyJsonResource {

    private final JobInfoProvider jobInfoProvider;

    /**
     * Creates a new instance which allows access to plans for jobs in the provider.
     */
    public JobsPlansResource(JobInfoProvider jobInfoProvider) {
        this.jobInfoProvider = jobInfoProvider;
    }

    /**
     * @see PlansQueries
     */
    @Path("{jobName}/plans")
    @GET
    public Response list(@PathParam("jobName") String jobName) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.list(planCoordinator.get().getPlanManagers());
    }

    /**
     * @see PlansQueries
     */
    @GET
    @Path("{jobName}/plans/{planName}")
    public Response get(@PathParam("jobName") String jobName, @PathParam("planName") String planName) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.get(planCoordinator.get().getPlanManagers(), planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{jobName}/plans/{planName}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(
            @PathParam("jobName") String jobName,
            @PathParam("planName") String planName,
            Map<String, String> parameters) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.start(planCoordinator.get().getPlanManagers(), planName, parameters);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{jobName}/plans/{planName}/stop")
    public Response stop(@PathParam("jobName") String jobName, @PathParam("planName") String planName) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.stop(planCoordinator.get().getPlanManagers(), planName);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{jobName}/plans/{planName}/continue")
    public Response continuePlan(
            @PathParam("jobName") String jobName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.continuePlan(planCoordinator.get().getPlanManagers(), planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{jobName}/plans/{planName}/interrupt")
    public Response interrupt(
            @PathParam("jobName") String jobName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.interrupt(planCoordinator.get().getPlanManagers(), planName, phase);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{jobName}/plans/{planName}/forceComplete")
    public Response forceComplete(
            @PathParam("jobName") String jobName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.forceComplete(planCoordinator.get().getPlanManagers(), planName, phase, step);
    }

    /**
     * @see PlansQueries
     */
    @POST
    @Path("{jobName}/plans/{planName}/restart")
    public Response restart(
            @PathParam("jobName") String jobName,
            @PathParam("planName") String planName,
            @QueryParam("phase") String phase,
            @QueryParam("step") String step) {
        Optional<PlanCoordinator> planCoordinator = jobInfoProvider.getPlanCoordinator(jobName);
        if (!planCoordinator.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PlansQueries.restart(planCoordinator.get().getPlanManagers(), planName, phase, step);
    }
}
