package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 * A read-only API for accessing file artifacts (e.g. config templates) for retrieval by pods.
 */
@Path("/v1/artifacts")
public class ArtifactResource {
  private static final String SERVICE_ARTIFACT_URI_FORMAT =
      "http://%s/v1/artifacts/template/%s/%s/%s/%s";

  private final ConfigStore<ServiceSpec> configStore;

  public ArtifactResource(ConfigStore<ServiceSpec> configStore) {
    this.configStore = configStore;
  }

  /**
   * Returns a factory for schedulers which use {@link ArtifactResource}.
   *
   * @param serviceName     the name of the service/framework (1:1)
   * @param schedulerConfig the scheduler config containing the configured API port
   */
  public static ArtifactQueries.TemplateUrlFactory getUrlFactory(
      String serviceName, SchedulerConfig schedulerConfig)
  {
    String hostnameAndPort = EndpointUtils.toSchedulerAutoIpEndpoint(serviceName, schedulerConfig);
    return (configId, podType, taskName, configName) -> String.format(
        SERVICE_ARTIFACT_URI_FORMAT,
        hostnameAndPort,
        configId,
        podType,
        taskName,
        configName);
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
      @PathParam("configurationName") String configurationName)
  {
    return ArtifactQueries.getTemplate(
        configStore,
        configurationId,
        podType,
        taskName,
        configurationName);
  }
}
