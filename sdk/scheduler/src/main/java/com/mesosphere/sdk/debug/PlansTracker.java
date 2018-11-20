package com.mesosphere.sdk.debug;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.json.JSONObject;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.PlanManager;

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
	
	public Response getJson(@QueryParam("plan") String u_plan,
							 @QueryParam("phase") String u_phase,
							 @QueryParam("step") String u_step,
							 @QueryParam("sync") boolean u_sync) {
	
		//TODO (kjoshi) Implement Pruning by plan/step/phase.
		JSONObject outcome = new JSONObject();
		for(PlanManager planManager: planCoordinator.getPlanManagers())
		{
			Plan plan  = planManager.getPlan();
			
			JSONObject planObject = new JSONObject();
			planObject.put("status", plan.getStatus());
			planObject.put("strategy", plan.getStrategy().getName());
			planObject.put("errors", plan.getErrors());
	
			//Iterate over phases.
			for(Phase phase: plan.getChildren())
			{
				JSONObject phaseObject = new JSONObject();
				phaseObject.put("status", phase.getStatus());
				phaseObject.put("strategy", phase.getStrategy().getName());
				phaseObject.put("errors", phase.getErrors());
			
				//Iterate over steps.
				for(Step step : phase.getChildren())
				{
					JSONObject stepObject = new JSONObject();
					stepObject.put("status", step.getStatus());
					stepObject.put("errors", step.getErrors());
					phaseObject.put("(step) "+step.getName(), stepObject);
				}

				planObject.put("(phase) "+phase.getName(), phaseObject);
			}
			
			outcome.put("(plan) "+plan.getName(), planObject);
		}
	
		return ResponseUtils.jsonOkResponse(outcome);
	}
}