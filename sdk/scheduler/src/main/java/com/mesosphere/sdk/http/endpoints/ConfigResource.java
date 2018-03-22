package com.mesosphere.sdk.http.endpoints;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.http.queries.ConfigQueries;
import com.mesosphere.sdk.http.types.PrettyJsonResource;
import com.mesosphere.sdk.state.ConfigStore;

/**
 * A read-only API for accessing active and inactive configurations from persistent storage.
 *
 * @param <T> The configuration type which is being stored by the service.
 */
@Path("/v1/configurations")
public class ConfigResource<T extends Configuration> extends PrettyJsonResource {

    private final ConfigStore<T> configStore;

    public ConfigResource(ConfigStore<T> configStore) {
        this.configStore = configStore;
    }

    /**
     * @see ConfigQueries
     */
    @GET
    public Response getConfigurationIds() {
        return ConfigQueries.<T>getConfigurationIds(configStore);
    }

    /**
     * @see ConfigQueries
     */
    @Path("/{configurationId}")
    @GET
    public Response getConfiguration(@PathParam("configurationId") String configurationId) {
        return ConfigQueries.<T>getConfiguration(configStore, configurationId);
    }

    /**
     * @see ConfigQueries
     */
    @Path("/targetId")
    @GET
    public Response getTargetId() {
        return ConfigQueries.<T>getTargetId(configStore);
    }

    /**
     * @see ConfigQueries
     */
    @Path("/target")
    @GET
    public Response getTarget() {
        return ConfigQueries.<T>getTarget(configStore);
    }
}
