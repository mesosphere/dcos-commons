package com.mesosphere.sdk.http.endpoints;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.EndpointsQueries;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.http.types.MultiServiceManager;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.state.StateStore;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Path("/v1/service")
public class MultiEndpointsResource {

    private final String frameworkName;
    private final MultiServiceManager multiServiceManager;
    private final SchedulerConfig schedulerConfig;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore},
     * using the provided {@code serviceName} for endpoint paths.
     */
    public MultiEndpointsResource(
            String frameworkName, MultiServiceManager multiServiceManager, SchedulerConfig schedulerConfig) {
        this.frameworkName = frameworkName;
        this.multiServiceManager = multiServiceManager;
        this.schedulerConfig = schedulerConfig;
    }

    /**
     * @see EndpointsQueries
     */
    @Path("{serviceName}/endpoints")
    @GET
    public Response getEndpoints(@PathParam("serviceName") String serviceName) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return EndpointsQueries.getEndpoints(
                stateStore.get(), frameworkName, getCustomEndpoints(serviceName), schedulerConfig);
    }

    /**
     * @see EndpointsQueries
     */
    @Path("{serviceName}/endpoints/{endpointName}")
    @GET
    public Response getEndpoint(
            @PathParam("serviceName") String serviceName,
            @PathParam("endpointName") String endpointName) {
        Optional<StateStore> stateStore = getStateStore(serviceName);
        if (!stateStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return EndpointsQueries.getEndpoint(
                stateStore.get(), frameworkName, getCustomEndpoints(serviceName), endpointName, schedulerConfig);
    }

    private Map<String, EndpointProducer> getCustomEndpoints(String serviceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
        return service.isPresent() ? service.get().getCustomEndpoints() : Collections.emptyMap();
    }

    private Optional<StateStore> getStateStore(String serviceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getService(serviceName);
        return service.isPresent() ? Optional.of(service.get().getStateStore()) : Optional.empty();
    }
}
