package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.debug.ReservationsTracker;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * A read-only API for accessing the current Mesos reservations held by the scheduler
 * for this service.
 */
@Path("/v1/debug")
public class ReservationsDebugResource {

  private final ReservationsTracker reservationsTracker;

  public ReservationsDebugResource(ReservationsTracker reservationsTracker) {
    this.reservationsTracker = reservationsTracker;
  }

  /**
   * Renders the current set of reservations.
   *
   * @return JSON response of the Reservations Endpoint.
   */
  @GET
  @Path("reservations")
  public Response getReservationss(@QueryParam("plan") String plan,
                                   @QueryParam("phase") String phase,
                                   @QueryParam("step") String step,
                                   @QueryParam("sync") boolean sync)
  {
    return reservationsTracker.getJson(plan, phase, step, sync);
  }
}
