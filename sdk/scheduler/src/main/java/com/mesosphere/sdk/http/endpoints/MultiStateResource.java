package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.StateQueries;
import com.mesosphere.sdk.http.types.PropertyDeserializer;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
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
@Path("/v1/service")
public class MultiStateResource {

    private final MultiServiceManager multiServiceManager;
    private final PropertyDeserializer propertyDeserializer;

    /**
     * Creates a new StateResource which returns content for runs in the provider.
     */
    public MultiStateResource(
            MultiServiceManager multiServiceManager,
            PropertyDeserializer propertyDeserializer) {
        this.multiServiceManager = multiServiceManager;
        this.propertyDeserializer = propertyDeserializer;
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/files")
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response getFiles(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.getFiles(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/files/{file}")
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response getFile(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("file") String fileName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.getFile(stateStore.get(), fileName);
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/files/{file}")
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response putFile(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @FormDataParam("file") InputStream uploadedInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetails) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.putFile(stateStore.get(), uploadedInputStream, fileDetails);
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/zone/tasks")
    @GET
    public Response getTaskNamesToZones(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.getTaskNamesToZones(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/zone/tasks/{taskName}")
    @GET
    public Response getTaskNameToZone(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("taskName") String taskName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.getTaskNameToZone(stateStore.get(), taskName);
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/zone/{podType}/{ip}")
    @GET
    public Response getTaskIPsToZones(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("podType") String podType,
            @PathParam("ip") String ip) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.getTaskIPsToZones(stateStore.get(), podType, ip);
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/properties")
    @GET
    public Response getPropertyKeys(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.getPropertyKeys(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/properties/{key}")
    @GET
    public Response getProperty(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("key") String key) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.getProperty(stateStore.get(), propertyDeserializer, key);
    }

    /**
     * @see StateQueries
     */
    @Path("{sanitizedServiceName}/state/refresh")
    @PUT
    public Response refreshCache(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return StateQueries.refreshCache(stateStore.get());
    }

    private Optional<StateStore> getStateStore(String sanitizedServiceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getServiceSanitized(sanitizedServiceName);
        return service.isPresent() ? Optional.of(service.get().getStateStore()) : Optional.empty();
    }
}
