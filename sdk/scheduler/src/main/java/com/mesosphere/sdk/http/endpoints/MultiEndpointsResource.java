package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.queries.EndpointsQueries;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.multi.MultiServiceManager;
import com.mesosphere.sdk.state.StateStore;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import java.util.Optional;

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
   * using the provided {@code sanitizedServiceName} for endpoint paths.
   */
  public MultiEndpointsResource(
      String frameworkName,
      MultiServiceManager multiServiceManager,
      SchedulerConfig schedulerConfig)
  {
    this.frameworkName = frameworkName;
    this.multiServiceManager = multiServiceManager;
    this.schedulerConfig = schedulerConfig;
  }

  /**
   * @see EndpointsQueries
   */
  @Path("{sanitizedServiceName}/endpoints")
  @GET
  public Response getEndpoints(@PathParam("sanitizedServiceName") String sanitizedServiceName) {
    Optional<AbstractScheduler> service =
        multiServiceManager.getServiceSanitized(sanitizedServiceName);
    if (!service.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return EndpointsQueries.getEndpoints(
        service.get().getStateStore(),
        frameworkName,
        service.get().getCustomEndpoints(),
        schedulerConfig);
  }

  /**
   * @see EndpointsQueries
   */
  @Path("{sanitizedServiceName}/endpoints/{endpointName}")
  @GET
  public Response getEndpoint(
      @PathParam("sanitizedServiceName") String sanitizedServiceName,
      @PathParam("endpointName") String endpointName)
  {
    Optional<AbstractScheduler> service =
        multiServiceManager.getServiceSanitized(sanitizedServiceName);
    if (!service.isPresent()) {
      return ResponseUtils.serviceNotFoundResponse(sanitizedServiceName);
    }
    return EndpointsQueries.getEndpoint(
        service.get().getStateStore(),
        frameworkName,
        service.get().getCustomEndpoints(),
        endpointName,
        schedulerConfig);
  }
}
