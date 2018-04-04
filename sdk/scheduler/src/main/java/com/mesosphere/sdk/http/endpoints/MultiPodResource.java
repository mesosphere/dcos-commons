package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.PodQueries;
import com.mesosphere.sdk.http.types.MultiServiceManager;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
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
    @Path("{serviceName}/pod")
    @GET
    public Response list(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PodQueries.list(stateStore.get());
    }

    /**
     * @see PodQueries
     */
    @Path("{serviceName}/pod/status")
    @GET
    public Response getStatuses(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PodQueries.getStatuses(stateStore.get(), serviceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{serviceName}/pod/{name}/status")
    @GET
    public Response getStatus(@PathParam("serviceName") String serviceName, @PathParam("name") String podInstanceName) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PodQueries.getStatus(stateStore.get(), podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{serviceName}/pod/{name}/info")
    @GET
    public Response getInfo(@PathParam("serviceName") String serviceName, @PathParam("name") String podInstanceName) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PodQueries.getInfo(stateStore.get(), podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("{serviceName}/pod/{name}/pause")
    @POST
    public Response pause(
            @PathParam("serviceName") String serviceName,
            @PathParam("name") String podInstanceName,
            String bodyPayload) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PodQueries.pause(stateStore.get(), podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("{serviceName}/pod/{name}/resume")
    @POST
    public Response resume(
            @PathParam("serviceName") String serviceName,
            @PathParam("name") String podInstanceName,
            String bodyPayload) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PodQueries.resume(stateStore.get(), podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("{serviceName}/pod/{name}/restart")
    @POST
    public Response restart(@PathParam("serviceName") String serviceName, @PathParam("name") String podInstanceName) {
        return restart(serviceName, podInstanceName, RecoveryType.TRANSIENT);
    }

    /**
     * @see PodQueries
     */
    @Path("{serviceName}/pod/{name}/replace")
    @POST
    public Response replace(@PathParam("serviceName") String serviceName, @PathParam("name") String podInstanceName) {
        return restart(serviceName, podInstanceName, RecoveryType.PERMANENT);
    }

    private Response restart(String serviceName, String podInstanceName, RecoveryType recoveryType) {
        Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
        if (!service.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return PodQueries.restart(
                service.get().getStateStore(), service.get().getConfigStore(), podInstanceName, recoveryType);
    }

    private Optional<StateStore> getStateStore(String serviceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
        return service.isPresent() ? Optional.of(service.get().getStateStore()) : Optional.empty();
    }
}
