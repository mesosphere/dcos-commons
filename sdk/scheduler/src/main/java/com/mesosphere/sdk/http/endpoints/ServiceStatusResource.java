package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.debug.ServiceStatusTracker;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * A read-only API for retrieving high-level information about the service. This works in conjunction with
 * the PlansResource and PlansDebugResource and isn't a replacement for them.
 */
@Path("/v1/status")
public class ServiceStatusResource {

  private final ServiceStatusTracker serviceStatusTracker;

  public ServiceStatusResource(ServiceStatusTracker serviceStatusTracker) {
    this.serviceStatusTracker = serviceStatusTracker;
  }

  /**
   * Returns the current service status.
   * @param isVerbose Toggle verbose output for further debugging.
   * @return
   */
  @GET
  @Path("check")
  public Response getServiceStatus(@QueryParam("verbose") boolean isVerbose) {
    return serviceStatusTracker.getJson(isVerbose);
  }
}
