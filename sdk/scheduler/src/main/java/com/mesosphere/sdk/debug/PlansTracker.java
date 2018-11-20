package com.mesosphere.sdk.debug;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.json.JSONObject;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;

public class PlansTracker implements DebugEndpoint {

	private PlanCoordinator planCoordinator;

	public PlansTracker(PlanCoordinator planCoordinator) {
		this.planCoordinator = planCoordinator;
	}
	
	private JSONObject dummyJSONObject() 
	{
		//Debugging dummy object.
		JSONObject outcome = new JSONObject();
		outcome.put("dummy_key", "dummy_value");
		return outcome;
	}
	
	public Response getJson(@QueryParam("plan") String plan,
							 @QueryParam("phase") String phase,
							 @QueryParam("step") String step,
							 @QueryParam("sync") boolean sync) {

		//TODO(kjoshi) Remove dummy return;
		return ResponseUtils.jsonOkResponse(dummyJSONObject());
	}
}