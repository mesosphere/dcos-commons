package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.StateQueries;
import com.mesosphere.sdk.http.types.MultiServiceInfoProvider;
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
@Path("/v1/service")
public class MultiStateResource {

    private final MultiServiceInfoProvider multiServiceInfoProvider;
    private final PropertyDeserializer propertyDeserializer;

    /**
     * Creates a new StateResource which returns content for runs in the provider.
     */
    public MultiStateResource(
            MultiServiceInfoProvider multiServiceInfoProvider,
            PropertyDeserializer propertyDeserializer) {
        this.multiServiceInfoProvider = multiServiceInfoProvider;
        this.propertyDeserializer = propertyDeserializer;
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/files")
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response getFiles(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.getFiles(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/files/{file}")
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response getFile(@PathParam("serviceName") String serviceName, @PathParam("file") String fileName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.getFile(stateStore.get(), fileName);
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/files/{file}")
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response putFile(
            @PathParam("serviceName") String serviceName,
            @FormDataParam("file") InputStream uploadedInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetails) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.putFile(stateStore.get(), uploadedInputStream, fileDetails);
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/zone/tasks")
    @GET
    public Response getTaskNamesToZones(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.getTaskNamesToZones(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/zone/tasks/{taskName}")
    @GET
    public Response getTaskNameToZone(
            @PathParam("serviceName") String serviceName, @PathParam("taskName") String taskName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.getTaskNameToZone(stateStore.get(), taskName);
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/zone/{podType}/{ip}")
    @GET
    public Response getTaskIPsToZones(
            @PathParam("serviceName") String serviceName,
            @PathParam("podType") String podType,
            @PathParam("ip") String ip) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.getTaskIPsToZones(stateStore.get(), podType, ip);
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/properties")
    @GET
    public Response getPropertyKeys(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.getPropertyKeys(stateStore.get());
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/properties/{key}")
    @GET
    public Response getProperty(@PathParam("serviceName") String serviceName, @PathParam("key") String key) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.getProperty(stateStore.get(), propertyDeserializer, key);
    }

    /**
     * @see StateQueries
     */
    @Path("{serviceName}/state/refresh")
    @PUT
    public Response refreshCache(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return StateQueries.refreshCache(stateStore.get());
    }
}
