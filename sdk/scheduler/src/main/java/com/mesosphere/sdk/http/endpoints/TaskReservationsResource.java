package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.debug.TaskReservationsTracker;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * A read-only API taskReservations.
 */

@Path("/v1/debug")
public class TaskReservationsResource {

  private final TaskReservationsTracker reservationsTracker;


  public TaskReservationsResource(TaskReservationsTracker reservationsTracker) {
    this.reservationsTracker = reservationsTracker;
  }

  /**
   * Renders the current set of TaskReservations.
   *
   * @return JSON response of the TaskReservations Endpoint.
   */
  @GET
  @Path("reservations")
  public Response getOfferOutcomes(@QueryParam("plan") String plan,
                                   @QueryParam("phase") String phase,
                                   @QueryParam("step") String step,
                                   @QueryParam("sync") boolean sync)
  {
    return reservationsTracker.getJson(plan, phase, step, sync);
  }
}
