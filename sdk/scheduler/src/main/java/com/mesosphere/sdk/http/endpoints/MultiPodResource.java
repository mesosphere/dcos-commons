package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.PodQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
import com.mesosphere.sdk.state.StateStore;

import java.util.Optional;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 * A read-only API for accessing information about the pods which compose the service, and restarting/replacing those
 * pods.
 */
@Singleton
@Path("/v1/service")
public class MultiPodResource extends PrettyJsonResource {

    private final MultiServiceManager multiServiceManager;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore}.
     */
    public MultiPodResource(MultiServiceManager multiServiceManager) {
        this.multiServiceManager = multiServiceManager;
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod")
    @GET
    public Response list(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PodQueries.list(stateStore.get());
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod/status")
    @GET
    public Response getStatuses(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PodQueries.getStatuses(stateStore.get(), sanitizedServiceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod/{name}/status")
    @GET
    public Response getStatus(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("name") String podInstanceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PodQueries.getStatus(stateStore.get(), podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod/{name}/info")
    @GET
    public Response getInfo(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("name") String podInstanceName) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PodQueries.getInfo(stateStore.get(), podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod/{name}/pause")
    @POST
    public Response pause(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("name") String podInstanceName,
            String bodyPayload) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PodQueries.pause(stateStore.get(), podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod/{name}/resume")
    @POST
    public Response resume(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("name") String podInstanceName,
            String bodyPayload) {
        Optional<StateStore> stateStore = getStateStore(sanitizedServiceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PodQueries.resume(stateStore.get(), podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod/{name}/restart")
    @POST
    public Response restart(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("name") String podInstanceName) {
        return restart(sanitizedServiceName, podInstanceName, false);
    }

    /**
     * @see PodQueries
     */
    @Path("{sanitizedServiceName}/pod/{name}/replace")
    @POST
    public Response replace(
            @PathParam("sanitizedServiceName") String sanitizedServiceName, @PathParam("name") String podInstanceName) {
        return restart(sanitizedServiceName, podInstanceName, true);
    }

    private Response restart(String sanitizedServiceName, String podInstanceName, boolean replace) {
        Optional<AbstractScheduler> service = multiServiceManager.getService(sanitizedServiceName);
        if (!service.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return PodQueries.restart(
                service.get().getStateStore(), service.get().getConfigStore(), podInstanceName, replace);
    }

    private Optional<StateStore> getStateStore(String sanitizedServiceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getService(sanitizedServiceName);
        return service.isPresent() ? Optional.of(service.get().getStateStore()) : Optional.empty();
    }
}
