package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.UUID;

/**
 * A read-only API for accessing file artifacts (e.g. config templates) for retrieval by pods.
 */
@Path("/v1/artifacts")
public class ArtifactResource {
    private static final String SERVICE_ARTIFACT_URI_FORMAT = "http://%s/v1/artifacts/template/%s/%s/%s/%s";

    private final ConfigStore<ServiceSpec> configStore;

    /**
     * Returns a factory for schedulers which use {@link ArtifactResource}.
     *
     * @param serviceName the name of the service/framework (1:1)
     */
    public static ArtifactQueries.TemplateUrlFactory getUrlFactory(String serviceName) {
        String hostname = EndpointUtils.toSchedulerApiVipHostname(serviceName);
        return new ArtifactQueries.TemplateUrlFactory() {
            @Override
            public String get(UUID configId, String podType, String taskName, String configName) {
                return String.format(SERVICE_ARTIFACT_URI_FORMAT, hostname, configId, podType, taskName, configName);
            }
        };
    }

    public ArtifactResource(ConfigStore<ServiceSpec> configStore) {
        this.configStore = configStore;
    }

    /**
     * @see ArtifactQueries
     */
    @Path("/template/{configurationId}/{podType}/{taskName}/{configurationName}")
    @GET
    public Response getTemplate(
            @PathParam("configurationId") String configurationId,
            @PathParam("podType") String podType,
            @PathParam("taskName") String taskName,
            @PathParam("configurationName") String configurationName) {
        return ArtifactQueries.getTemplate(configStore, configurationId, podType, taskName, configurationName);
    }
}
