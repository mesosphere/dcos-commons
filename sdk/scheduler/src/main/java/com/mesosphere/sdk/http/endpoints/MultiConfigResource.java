package com.mesosphere.sdk.http.endpoints;

import java.util.Optional;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.ConfigQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;

/**
 * A read-only API for accessing active and inactive configurations from persistent storage.
 */
@Singleton
@Path("/v1/service")
public class MultiConfigResource extends PrettyJsonResource {

    private final MultiServiceManager multiServiceManager;

    public MultiConfigResource(MultiServiceManager multiServiceManager) {
        this.multiServiceManager = multiServiceManager;
    }

    /**
     * @see ConfigQueries
     */
    @Path("{sanitizedServiceName}/configurations")
    @GET
    public Response getConfigurationIds(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<ConfigStore<ServiceSpec>> configStore = getConfigStore(sanitizedServiceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return ConfigQueries.<ServiceSpec>getConfigurationIds(configStore.get());
    }

    /**
     * @see ConfigQueries
     */
    @Path("{sanitizedServiceName}/configurations/{configurationId}")
    @GET
    public Response getConfiguration(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("configurationId") String configurationId) {
        Optional<ConfigStore<ServiceSpec>> configStore = getConfigStore(sanitizedServiceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return ConfigQueries.<ServiceSpec>getConfiguration(configStore.get(), configurationId);
    }

    /**
     * @see ConfigQueries
     */
    @Path("{sanitizedServiceName}/configurations/targetId")
    @GET
    public Response getTargetId(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<ConfigStore<ServiceSpec>> configStore = getConfigStore(sanitizedServiceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return ConfigQueries.getTargetId(configStore.get());
    }

    /**
     * @see ConfigQueries
     */
    @Path("{sanitizedServiceName}/configurations/target")
    @GET
    public Response getTarget(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
        Optional<ConfigStore<ServiceSpec>> configStore = getConfigStore(sanitizedServiceName);
        if (!configStore.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return ConfigQueries.getTarget(configStore.get());
    }

    private Optional<ConfigStore<ServiceSpec>> getConfigStore(String sanitizedServiceName) {
        Optional<AbstractScheduler> service = multiServiceManager.getServiceSanitized(sanitizedServiceName);
        return service.isPresent() ? Optional.of(service.get().getConfigStore()) : Optional.empty();
    }
}
