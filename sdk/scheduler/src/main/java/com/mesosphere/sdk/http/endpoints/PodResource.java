package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.queries.PodQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

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
@Path("/v1/pod")
public class PodResource extends PrettyJsonResource {

    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;
    private final String serviceName;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore}.
     */
    public PodResource(StateStore stateStore, ConfigStore<ServiceSpec> configStore, String serviceName) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.serviceName = serviceName;
    }

    /**
     * @see PodQueries
     */
    @GET
    public Response list() {
        return PodQueries.list(stateStore);
    }

    /**
     * @see PodQueries
     */
    @Path("/status")
    @GET
    public Response getStatuses() {
        return PodQueries.getStatuses(stateStore, serviceName);
    }

    /**
     * @see PodQueries
     */
    @Path("/{name}/status")
    @GET
    public Response getStatus(@PathParam("name") String podInstanceName) {
        return PodQueries.getStatus(stateStore, podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("/{name}/info")
    @GET
    public Response getInfo(@PathParam("name") String podInstanceName) {
        return PodQueries.getInfo(stateStore, podInstanceName);
    }

    /**
     * @see PodQueries
     */
    @Path("/{name}/pause")
    @POST
    public Response pause(@PathParam("name") String podInstanceName, String bodyPayload) {
        return PodQueries.pause(stateStore, podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("/{name}/resume")
    @POST
    public Response resume(@PathParam("name") String podInstanceName, String bodyPayload) {
        return PodQueries.resume(stateStore, podInstanceName, bodyPayload);
    }

    /**
     * @see PodQueries
     */
    @Path("/{name}/restart")
    @POST
    public Response restart(@PathParam("name") String podInstanceName) {
        return PodQueries.restart(stateStore, configStore, podInstanceName, RecoveryType.TRANSIENT);
    }

    /**
     * @see PodQueries
     */
    @Path("/{name}/replace")
    @POST
    public Response replace(@PathParam("name") String podInstanceName) {
        return PodQueries.restart(stateStore, configStore, podInstanceName, RecoveryType.PERMANENT);
    }
}
