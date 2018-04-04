package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.Optional;
import java.util.UUID;

/**
 * A read-only API for accessing file artifacts (e.g. config templates) for retrieval by pods.
 */
@Path("/v1/service")
public class MultiArtifactResource {

    private static final String RUN_ARTIFACT_URI_FORMAT = "http://%s/v1/service/%s/artifacts/template/%s/%s/%s/%s";

    private final MultiServiceManager multiServiceManager;

    public MultiArtifactResource(MultiServiceManager multiServiceManager) {
        this.multiServiceManager = multiServiceManager;
    }

    /**
     * Returns a factory for schedulers which use {@link MultiArtifactResource}.
     *
     * @param frameworkName the name of the scheduler framework
     * @param serviceName the name of a service being managed by the scheduler
     */
    public static ArtifactQueries.TemplateUrlFactory getUrlFactory(String frameworkName, String serviceName) {
        String hostname = EndpointUtils.toSchedulerApiVipHostname(frameworkName);
        // Replace slashes with periods:
        String sanitizedServiceName = CommonIdUtils.toSanitizedServiceName(serviceName);
        return new ArtifactQueries.TemplateUrlFactory() {
            @Override
            public String get(UUID configId, String podType, String taskName, String configName) {
                return String.format(RUN_ARTIFACT_URI_FORMAT,
                        hostname, sanitizedServiceName, configId, podType, taskName, configName);
            }
        };
    }

    /**
     * @see ArtifactQueries
     */
    @Path("{sanitizedServiceName}/artifacts/template/{configurationId}/{podType}/{taskName}/{configurationName}")
    @GET
    public Response getTemplate(
            @PathParam("sanitizedServiceName") String sanitizedServiceName,
            @PathParam("configurationId") String configurationId,
            @PathParam("podType") String podType,
            @PathParam("taskName") String taskName,
            @PathParam("configurationName") String configurationName) {
        // Use custom fetch function, as any slashes should have been replaced with periods (see getUrlFactory()):
        Optional<AbstractScheduler> service = multiServiceManager.getServiceSanitized(sanitizedServiceName);
        if (!service.isPresent()) {
            return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
        }
        return ArtifactQueries.getTemplate(
                service.get().getConfigStore(), configurationId, podType, taskName, configurationName);
    }
}
