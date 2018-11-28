package com.mesosphere.sdk.http.endpoints;
import com.mesosphere.sdk.debug.TaskStatusesTracker;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
/**
 * A read-only API for accessing the current Plan tree, including custom plans.
 */
@Path("/v1/debug")
public class TaskStatusesResource {

  private final TaskStatusesTracker statusesTracker;
  public TaskStatusesResource(TaskStatusesTracker statusesTracker) {
    this.statusesTracker = statusesTracker;
  }
  /**
   * Renders the current set of Plan tree.
   *
   * @return JSON response of the Plans Endpoint.
   */
  @GET
  @Path("taskStatuses")
  public Response getOfferOutcomes(@QueryParam("plan") String plan,
                                   @QueryParam("phase") String phase,
                                   @QueryParam("step") String step,
                                   @QueryParam("sync") boolean sync) {
    return statusesTracker.getJson(plan, phase, step, sync);
  }
}
