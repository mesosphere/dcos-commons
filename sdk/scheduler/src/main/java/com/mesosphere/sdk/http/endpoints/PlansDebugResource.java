package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.debug.PlansTracker;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;


/**
 * A read-only API for accessing the current Plan tree, including custom plans.
 */
@Path("/v1/debug")
public class PlansDebugResource {

  private final PlansTracker plansTracker;

  public PlansDebugResource(PlansTracker plansTracker) {
    this.plansTracker = plansTracker;
  }

  /**
   * Renders the current set of Plan tree.
   *
   * @return JSON response of the Plans Endpoint.
   */
  @GET
  @Path("plans")
  public Response getOfferOutcomes(@QueryParam("plan") String plan,
                  @QueryParam("phase") String phase,
                  @QueryParam("step") String step,
                  @QueryParam("sync") boolean sync)
  {
    return plansTracker.getJson(plan, phase, step, sync);
  }
}
