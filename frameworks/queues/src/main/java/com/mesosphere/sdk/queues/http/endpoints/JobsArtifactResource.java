package com.mesosphere.sdk.queues.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.queues.http.types.JobInfoProvider;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.Optional;

/**
 * A read-only API for accessing file artifacts (e.g. config templates) for retrieval by pods.
 */
@Path("/v1/jobs")
public class JobsArtifactResource {

    private final JobInfoProvider jobInfoProvider;

    public JobsArtifactResource(JobInfoProvider jobInfoProvider) {
        this.jobInfoProvider = jobInfoProvider;
    }

    /**
     * @see ArtifactQueries
     */
    @Path("{jobName}/artifacts/template/{configurationId}/{podType}/{taskName}/{configurationName}")
    @GET
    public Response getTemplate(
            @PathParam("jobName") String jobName,
            @PathParam("configurationId") String configurationId,
            @PathParam("podType") String podType,
            @PathParam("taskName") String taskName,
            @PathParam("configurationName") String configurationName) {
        Optional<ConfigStore<ServiceSpec>> configStore = jobInfoProvider.getConfigStore(jobName);
        if (!configStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return ArtifactQueries.getTemplate(configStore.get(), configurationId, podType, taskName, configurationName);
    }
}
