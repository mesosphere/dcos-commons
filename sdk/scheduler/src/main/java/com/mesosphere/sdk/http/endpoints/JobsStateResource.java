package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.StateQueries;
import com.mesosphere.sdk.http.types.JobInfoProvider;
import com.mesosphere.sdk.http.types.PropertyDeserializer;
import com.mesosphere.sdk.state.StateStore;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.Optional;

/**
 * An API for reading task and frameworkId state from persistent storage, and resetting the state store cache if one is
 * being used.
 */
@Path("/v1/jobs")
public class JobsStateResource {

    private final JobInfoProvider jobInfoProvider;
    private final PropertyDeserializer propertyDeserializer;

    /**
     * Creates a new StateResource which returns content for jobs in the provider.
     */
    public JobsStateResource(JobInfoProvider jobInfoProvider, PropertyDeserializer propertyDeserializer) {
        this.jobInfoProvider = jobInfoProvider;
        this.propertyDeserializer = propertyDeserializer;
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/files")
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response getFiles(@PathParam("jobName") String jobName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.getFiles(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/files/{file}")
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response getFile(@PathParam("jobName") String jobName, @PathParam("file") String fileName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.getFile(stateStore.get(), fileName);
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/files/{file}")
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response putFile(
            @PathParam("jobName") String jobName,
            @FormDataParam("file") InputStream uploadedInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetails) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.putFile(stateStore.get(), uploadedInputStream, fileDetails);
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/zone/tasks")
    @GET
    public Response getTaskNamesToZones(@PathParam("jobName") String jobName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.getTaskNamesToZones(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/zone/tasks/{taskName}")
    @GET
    public Response getTaskNameToZone(@PathParam("jobName") String jobName, @PathParam("taskName") String taskName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.getTaskNameToZone(stateStore.get(), taskName);
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/zone/{podType}/{ip}")
    @GET
    public Response getTaskIPsToZones(
            @PathParam("jobName") String jobName, @PathParam("podType") String podType, @PathParam("ip") String ip) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.getTaskIPsToZones(stateStore.get(), podType, ip);
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/properties")
    @GET
    public Response getPropertyKeys(@PathParam("jobName") String jobName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.getPropertyKeys(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/properties/{key}")
    @GET
    public Response getProperty(@PathParam("jobName") String jobName, @PathParam("key") String key) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.getProperty(stateStore.get(), propertyDeserializer, key);
    }

    /**
     * @see StateQueries
     */
    @Path("{jobName}/state/refresh")
    @PUT
    public Response refreshCache(@PathParam("jobName") String jobName) {
        Optional<StateStore> stateStore = jobInfoProvider.getStateStore(jobName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.jobNotFoundResponse(jobName);
        }
        return StateQueries.refreshCache(stateStore.get());
    }
}
