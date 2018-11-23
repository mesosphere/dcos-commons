package com.mesosphere.sdk.http.endpoints;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * Implements a set of deprecated {@code /v1/plan/} endpoints which operate against the Deploy plan.
 */
@Path("/v1/plan")
public class DeprecatedPlanResource {

  private static final String PLAN = "deploy";

  private final PlansResource plans;

  public DeprecatedPlanResource(PlansResource plans) {
    this.plans = plans;
  }

  @GET
  @Deprecated
  public Response get() {
    return plans.get(PLAN);
  }

  @POST
  @Deprecated
  @Path("continue")
  public Response continuePlan() {
    return plans.continuePlan(PLAN, null);
  }

  @POST
  @Deprecated
  @Path("interrupt")
  public Response interrupt() {
    return plans.interrupt(PLAN, null);
  }

  @POST
  @Deprecated
  @Path("forceComplete")
  public Response forceComplete(
      @QueryParam("phase") String phaseId,
      @QueryParam("step") String stepId)
  {
    return plans.forceComplete(PLAN, phaseId, stepId);
  }

  @POST
  @Deprecated
  @Path("restart")
  public Response restart(@QueryParam("phase") String phaseId, @QueryParam("step") String stepId) {
    return plans.restart(PLAN, phaseId, stepId);
  }
}
