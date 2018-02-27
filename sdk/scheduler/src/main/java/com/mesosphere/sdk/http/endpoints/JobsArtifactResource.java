package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.http.types.JobInfoProvider;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.Optional;
import java.util.UUID;

/**
 * A read-only API for accessing file artifacts (e.g. config templates) for retrieval by pods.
 */
@Path("/v1/jobs")
public class JobsArtifactResource {
    private static final String JOB_ARTIFACT_URI_FORMAT = "http://%s/v1/jobs/%s/artifacts/template/%s/%s/%s/%s";

    /**
     * Returns a valid URL for accessing a config template artifact from a job task.
     * Must be kept in sync with {@link JobsArtifactResource#getTemplate(String, String, String, String)}.
     *
     * @see ArtifactResource#getJobTemplateUrl
     */
    public static String getJobTemplateUrl(
            String queueName, String jobName, UUID configId, String podType, String taskName, String configName) {
        return String.format(JOB_ARTIFACT_URI_FORMAT,
                EndpointUtils.toSchedulerApiVipHostname(queueName), jobName, configId, podType, taskName, configName);
    }

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
