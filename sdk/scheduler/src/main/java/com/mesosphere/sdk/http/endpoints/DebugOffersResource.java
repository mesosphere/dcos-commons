package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.debug.OfferOutcomeTrackerV2;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * A read-only API for Offers.
 */
@Path("/v2/debug")
public class DebugOffersResource {

  private final OfferOutcomeTrackerV2 offersTracker;

  public DebugOffersResource(OfferOutcomeTrackerV2 statusesTracker) {
    this.offersTracker = statusesTracker;
  }

  /**
   * Renders the offer summary.
   *
   * @return JSON response of the DebugOffers Endpoint.
   */
  @GET
  @Path("offers")
  public Response getOfferOutcomes(@QueryParam("plan") String plan,
                                   @QueryParam("phase") String phase,
                                   @QueryParam("step") String step,
                                   @QueryParam("sync") boolean sync)
  {
    return offersTracker.getJson(plan, phase, step, sync);
  }
}
