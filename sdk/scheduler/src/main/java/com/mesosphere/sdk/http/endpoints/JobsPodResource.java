package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.PodQueries;
import com.mesosphere.sdk.http.types.JobInfoProvider;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 * A read-only API for accessing information about the pods which compose the service, and restarting/replacing those
 * pods.
 */
@Path("/v1/jobs")
public class JobsPodResource extends PrettyJsonResource {

    private final JobInfoProvider jobInfoProvider;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore}.
     */
    public JobsPodResource(JobInfoProvider jobInfoProvider) {
        this.jobInfoProvider = jobInfoProvider;
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod")
    @GET
    public Response list(@PathParam("jobName") String jobName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PodQueries.list(stateStore.get());
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod/status")
    @GET
    public Response getStatuses(@PathParam("jobName") String jobName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PodQueries.getStatuses(stateStore.get(), jobName);
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod/{name}/status")
    @GET
    public Response getStatus(@PathParam("jobName") String jobName, @PathParam("name") String podInstanceName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PodQueries.getStatus(stateStore.get(), podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod/{name}/info")
    @GET
    public Response getInfo(@PathParam("jobName") String jobName, @PathParam("name") String podInstanceName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PodQueries.getInfo(stateStore.get(), podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod/{name}/pause")
    @POST
    public Response pause(
            @PathParam("jobName") String jobName, @PathParam("name") String podInstanceName, String bodyPayload) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PodQueries.pause(stateStore.get(), podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod/{name}/resume")
    @POST
    public Response resume(
            @PathParam("jobName") String jobName, @PathParam("name") String podInstanceName, String bodyPayload) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PodQueries.resume(stateStore.get(), podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod/{name}/restart")
    @POST
    public Response restart(@PathParam("jobName") String jobName, @PathParam("name") String podInstanceName) {
        return restart(jobName, podInstanceName, RecoveryType.TRANSIENT);
    }

    /**
     * @see PodQueries
     */
    @Path("{jobName}/pod/{name}/replace")
    @POST
    public Response replace(@PathParam("jobName") String jobName, @PathParam("name") String podInstanceName) {
        return restart(jobName, podInstanceName, RecoveryType.PERMANENT);
    }

    private Response restart(String jobName, String podInstanceName, RecoveryType recoveryType) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        Optional<ConfigStore<ServiceSpec>> configStore = jobInfoProvider.getConfigStore(jobName);
        if (!configStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return PodQueries.restart(stateStore.get(), configStore.get(), podInstanceName, recoveryType);
    }
}
