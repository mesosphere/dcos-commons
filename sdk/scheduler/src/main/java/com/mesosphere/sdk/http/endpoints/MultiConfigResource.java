package com.mesosphere.sdk.http.endpoints;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.ConfigQueries;
import com.mesosphere.sdk.http.types.MultiServiceInfoProvider;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;

/**
 * A read-only API for accessing active and inactive configurations from persistent storage.
 */
@Path("/v1/service")
public class MultiConfigResource extends PrettyJsonResource {

    private final MultiServiceInfoProvider multiServiceInfoProvider;

    public MultiConfigResource(MultiServiceInfoProvider multiServiceInfoProvider) {
        this.multiServiceInfoProvider = multiServiceInfoProvider;
    }

    /**
     * @see ConfigQueries
     */
    @Path("{serviceName}/configurations")
    @GET
    public Response getConfigurationIds(@PathParam("serviceName") String serviceName) {
        Optional<ConfigStore<ServiceSpec>> configStore = multiServiceInfoProvider.getConfigStore(serviceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return ConfigQueries.<ServiceSpec>getConfigurationIds(configStore.get());
    }

    /**
     * @see ConfigQueries
     */
    @Path("{serviceName}/configurations/{configurationId}")
    @GET
    public Response getConfiguration(
            @PathParam("serviceName") String serviceName, @PathParam("configurationId") String configurationId) {
        Optional<ConfigStore<ServiceSpec>> configStore = multiServiceInfoProvider.getConfigStore(serviceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return ConfigQueries.<ServiceSpec>getConfiguration(configStore.get(), configurationId);
    }

    /**
     * @see ConfigQueries
     */
    @Path("{serviceName}/configurations/targetId")
    @GET
    public Response getTargetId(@PathParam("serviceName") String serviceName) {
        Optional<ConfigStore<ServiceSpec>> configStore = multiServiceInfoProvider.getConfigStore(serviceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return ConfigQueries.getTargetId(configStore.get());
    }

    /**
     * @see ConfigQueries
     */
    @Path("{serviceName}/configurations/target")
    @GET
    public Response getTarget(@PathParam("serviceName") String serviceName) {
        Optional<ConfigStore<ServiceSpec>> configStore = multiServiceInfoProvider.getConfigStore(serviceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(serviceName);
        }
        return ConfigQueries.getTarget(configStore.get());
    }
}
