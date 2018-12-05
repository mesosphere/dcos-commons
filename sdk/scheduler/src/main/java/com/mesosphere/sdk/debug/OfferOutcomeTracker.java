package com.mesosphere.sdk.debug;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

public class OfferOutcomeTracker implements DebugEndpoint {

  public Response getJson(
      @QueryParam("plan") String plan,
      @QueryParam("phase") String phase,
      @QueryParam("step") String step,
      @QueryParam("sync") boolean sync)
  {
    return null;
  }

}
