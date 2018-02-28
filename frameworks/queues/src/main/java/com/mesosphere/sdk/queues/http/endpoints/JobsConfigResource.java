package com.mesosphere.sdk.queues.http.endpoints;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.ConfigQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.queues.http.types.JobInfoProvider;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;

/**
 * A read-only API for accessing active and inactive configurations from persistent storage.
 */
@Path("/v1/jobs")
public class JobsConfigResource extends PrettyJsonResource {

    private final JobInfoProvider jobInfoProvider;

    public JobsConfigResource(JobInfoProvider jobInfoProvider) {
        this.jobInfoProvider = jobInfoProvider;
    }

    /**
     * @see ConfigQueries
     */
    @Path("{jobName}/configurations")
    @GET
    public Response getConfigurationIds(@PathParam("jobName") String jobName) {
        Optional<ConfigStore<ServiceSpec>> configStore = jobInfoProvider.getConfigStore(jobName);
        if (!configStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return ConfigQueries.<ServiceSpec>getConfigurationIds(configStore.get());
    }

    /**
     * @see ConfigQueries
     */
    @Path("{jobName}/configurations/{configurationId}")
    @GET
    public Response getConfiguration(
            @PathParam("jobName") String jobName, @PathParam("configurationId") String configurationId) {
        Optional<ConfigStore<ServiceSpec>> configStore = jobInfoProvider.getConfigStore(jobName);
        if (!configStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return ConfigQueries.<ServiceSpec>getConfiguration(configStore.get(), configurationId);
    }

    /**
     * @see ConfigQueries
     */
    @Path("{jobName}/configurations/targetId")
    @GET
    public Response getTargetId(@PathParam("jobName") String jobName) {
        Optional<ConfigStore<ServiceSpec>> configStore = jobInfoProvider.getConfigStore(jobName);
        if (!configStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return ConfigQueries.getTargetId(configStore.get());
    }

    /**
     * @see ConfigQueries
     */
    @Path("{jobName}/configurations/target")
    @GET
    public Response getTarget(@PathParam("jobName") String jobName) {
        Optional<ConfigStore<ServiceSpec>> configStore = jobInfoProvider.getConfigStore(jobName);
        if (!configStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return ConfigQueries.getTarget(configStore.get());
    }
}
