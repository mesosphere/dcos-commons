package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.state.StateStore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.Set;

/**
 * TaskReservationsTracker is the backend of TaskReservationsResource
 * It aggregates and reports reservations owned by this service.
 */
public class TaskReservationsTracker implements DebugEndpoint {

  private final StateStore stateStore;

  public TaskReservationsTracker(StateStore stateStore) {
    this.stateStore = stateStore;
  }

  public Response getJson(@QueryParam("plan") String filterPlan,
                          @QueryParam("phase") String filterPhase,
                          @QueryParam("step") String filterStep,
                          @QueryParam("sync") boolean requireSync)
  {
    Map<String, Set<String>> resourceIdsByAgentHost = SchedulerUtils.getResourceIdsByAgentHost(stateStore);

    ObjectMapper jsonMapper = new ObjectMapper();
    jsonMapper.registerModule(new Jdk8Module());
    jsonMapper.registerModule(new JsonOrgModule());

    JSONObject response = jsonMapper.convertValue(resourceIdsByAgentHost, JSONObject.class);

    return ResponseUtils.jsonOkResponse(response);
  }
}
