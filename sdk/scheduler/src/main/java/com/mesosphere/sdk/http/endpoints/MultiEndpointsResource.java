package com.mesosphere.sdk.http.endpoints;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.EndpointsQueries;
import com.mesosphere.sdk.http.types.MultiServiceInfoProvider;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.state.StateStore;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Path("/v1/service")
public class MultiEndpointsResource {

    private final String frameworkName;
    private final MultiServiceInfoProvider multiServiceInfoProvider;
    private final SchedulerConfig schedulerConfig;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore},
     * using the provided {@code serviceName} for endpoint paths.
     */
    public MultiEndpointsResource(
            String frameworkName, MultiServiceInfoProvider multiServiceInfoProvider, SchedulerConfig schedulerConfig) {
        this.frameworkName = frameworkName;
        this.multiServiceInfoProvider = multiServiceInfoProvider;
        this.schedulerConfig = schedulerConfig;
    }

    /**
     * @see EndpointsQueries
     */
    @Path("{serviceName}/endpoints")
    @GET
    public Response getEndpoints(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return EndpointsQueries.getEndpoints(
                stateStore.get(),
                frameworkName,
                multiServiceInfoProvider.getCustomEndpoints(serviceName),
                schedulerConfig);
    }

    /**
     * @see EndpointsQueries
     */
    @Path("{serviceName}/endpoints/{endpointName}")
    @GET
    public Response getEndpoint(
            @PathParam("serviceName") String serviceName,
            @PathParam("endpointName") String endpointName) {
        Optional<StateStore> stateStore = multiServiceInfoProvider.getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return EndpointsQueries.getEndpoint(
                stateStore.get(),
                frameworkName,
                multiServiceInfoProvider.getCustomEndpoints(serviceName),
                endpointName,
                schedulerConfig);
    }

}
