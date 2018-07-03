package com.mesosphere.sdk.http.endpoints;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.http.queries.ConfigQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;

/**
 * A read-only API for accessing active and inactive configurations from persistent storage.
 */
@Singleton
@Path("/v1/configurations")
public class ConfigResource extends PrettyJsonResource {

    private final ConfigStore<ServiceSpec> configStore;

    public ConfigResource(ConfigStore<ServiceSpec> configStore) {
        this.configStore = configStore;
    }

    /**
     * @see ConfigQueries
     */
    @GET
    public Response getConfigurationIds() {
        return ConfigQueries.<ServiceSpec>getConfigurationIds(configStore);
    }

    /**
     * @see ConfigQueries
     */
    @Path("/{configurationId}")
    @GET
    public Response getConfiguration(@PathParam("configurationId") String configurationId) {
        return ConfigQueries.<ServiceSpec>getConfiguration(configStore, configurationId);
    }

    /**
     * @see ConfigQueries
     */
    @Path("/targetId")
    @GET
    public Response getTargetId() {
        return ConfigQueries.<ServiceSpec>getTargetId(configStore);
    }

    /**
     * @see ConfigQueries
     */
    @Path("/target")
    @GET
    public Response getTarget() {
        return ConfigQueries.<ServiceSpec>getTarget(configStore);
    }
}
