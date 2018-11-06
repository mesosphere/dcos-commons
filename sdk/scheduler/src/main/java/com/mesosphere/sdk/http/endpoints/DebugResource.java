package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;

import com.codahale.metrics.jvm.ThreadDump;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.lang.management.ManagementFactory;

/**
 * A read-only API for accessing the most recently processed offers. It does _not_ return any information
 * about offers that were declined but never evaluated.
 */
@Path("/v1/debug")
public class DebugResource {
  private final OfferOutcomeTracker offerOutcomeTracker;

  private final ThreadDump threadDump = new ThreadDump(ManagementFactory.getThreadMXBean());

  public DebugResource(OfferOutcomeTracker offerOutcomeTracker) {
    this.offerOutcomeTracker = offerOutcomeTracker;
  }

  /**
   * Renders the current set of offer outcomes as an HTML table.
   *
   * @return HTML response of the table.
   */
  @GET
  @Path("offers")
  public Response getOfferOutcomes(@QueryParam("json") boolean json) {
    if (json) {
      return ResponseUtils.jsonOkResponse(offerOutcomeTracker.toJson());
    } else {
      return ResponseUtils.htmlOkResponse(offerOutcomeTracker.toHtml());
    }
  }

  /**
   * Renders the current threads and thread state of the scheduler.
   */
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("threads")
  public StreamingOutput getThreads() {
    return output -> {
      threadDump.dump(output);
    };
  }
}
