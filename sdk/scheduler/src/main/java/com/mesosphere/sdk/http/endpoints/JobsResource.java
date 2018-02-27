package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.types.JobInfoProvider;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.json.JSONArray;

/**
 * A read-only API for listing available jobs.
 */
@Path("/v1/jobs")
public class JobsResource {

    private final JobInfoProvider jobInfoProvider;

    public JobsResource(JobInfoProvider jobInfoProvider) {
        this.jobInfoProvider = jobInfoProvider;
    }

    /**
     * Returns a list of active jobs.
     */
    @GET
    public Response getJobs() {
        return ResponseUtils.jsonOkResponse(new JSONArray(jobInfoProvider.getJobs()));
    }
}
