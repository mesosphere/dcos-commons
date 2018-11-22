package com.mesosphere.sdk.debug;

import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;

public class PlansTracker implements DebugEndpoint {

	private final PlanCoordinator planCoordinator;

	public PlansTracker(PlanCoordinator planCoordinator) {
		this.planCoordinator = planCoordinator;
	}
	
	private HashMap<String, JSONArray> generateServiceTopology() {
		
		HashMap<String, JSONArray> plansTree = new HashMap<String, JSONArray>();
		for(PlanManager planManager: planCoordinator.getPlanManagers())
		{
			Plan plan = planManager.getPlan();
			JSONArray phasesArray = new JSONArray();
			for(Phase phase: plan.getChildren())
			{
				HashMap<String, JSONArray> phaseTree = new HashMap<String, JSONArray>();
				JSONArray stepsArray = new JSONArray();
				for(Step step: phase.getChildren())
				{
					stepsArray.put(step.getName());
				}
				phaseTree.put(phase.getName(), stepsArray);
				phasesArray.put(phaseTree);
			}
			plansTree.put(plan.getName(), phasesArray);
		}
		return plansTree;
	}
	
	private JSONObject generateServiceStatus(String u_plan, String u_phase, String u_step) {
		JSONObject outcome = new JSONObject();
		JSONArray plansArray = new JSONArray();
		
		for(PlanManager planManager: planCoordinator.getPlanManagers())
		{
			Plan plan  = planManager.getPlan();

			//Filter down to a plan if specified.
			if(u_plan != null && !plan.getName().equalsIgnoreCase(u_plan))
				continue;
			
			JSONObject planObject = new JSONObject();
			JSONArray phaseArray = new JSONArray();
			planObject.put("name", plan.getName());
			planObject.put("status", plan.getStatus());
			planObject.put("strategy", plan.getStrategy().getName());
			planObject.put("phases", phaseArray);
		
			//Get a rollup aggregation of the steps.
			int totalSteps = plan.getChildren().stream()
									.flatMap(phase -> phase.getChildren().stream())
									.collect(Collectors.toSet())
									.size();
			int completedSteps = plan.getChildren().stream()
									.flatMap(phase -> phase.getChildren().stream())
									.filter(step -> step.isComplete())
									.collect(Collectors.toSet())
									.size();
			
			planObject.put("total-steps", totalSteps);
			planObject.put("completed-steps", completedSteps);
			
			//Iterate over phases.
			for(Phase phase: plan.getChildren())
			{
				//Filter down to a phase if specified.
				if(u_phase != null && !phase.getName().equalsIgnoreCase(u_phase))
					continue;
				
				JSONObject phaseObject = new JSONObject();
				JSONArray stepArray = new JSONArray();
				
				phaseObject.put("name", phase.getName());
				phaseObject.put("status", phase.getStatus());
				phaseObject.put("strategy", phase.getStrategy().getName());
				phaseObject.put("steps", stepArray);
			
				//Iterate over steps.
				for(Step step : phase.getChildren())
				{
					//Filter down to a step if specified.
					if(u_step != null && !step.getName().equalsIgnoreCase(u_step))
						continue;
					
					JSONObject stepObject = new JSONObject();
					stepObject.put("name", step.getName());
					stepObject.put("status", step.getStatus());
					stepObject.put("errors", step.getErrors());
					
					stepArray.put(stepObject);
				}
			
				phaseArray.put(phaseObject);
			}
			
			plansArray.put(planObject);
		}
		outcome.put("plans", plansArray);
		return outcome;		
	}

	private JSONObject getValidationErrorResponse(String u_plan, String u_phase, String u_step)
	{
		//If a step is defined ensure both plan and phase are also provided.
		if(u_step != null && (u_plan == null || u_phase == null))
		{
			JSONObject outcome = new JSONObject();
			return outcome.put("invalid_input", "Step specified without parent Phase and Plan values.");			
		}
	
		//If a phase is defined ensure parent plan is also provided.
		if(u_phase != null && u_plan == null)
		{
			JSONObject outcome = new JSONObject();
			return outcome.put("invalid_input", "Phase specified without parent Plan.");			
		}
		
		//Ensure correct ownership. Start with the plan.
		
		//If no explicit plan defined, nothing further to do.
		if(u_plan == null) return null;
	
		//Plan is specified, ensure it exists within our list of plans.
		//Filter for our desired plan.
		List<PlanManager> planManagers = planCoordinator.getPlanManagers().stream()
														.filter((planManger -> planManger.getPlan().getName().equalsIgnoreCase(u_plan)))
														.collect(Collectors.toList());
		//Ensure we found our plan.
		if(planManagers.size() != 1)
		{
			JSONObject outcome = new JSONObject();
			return outcome.put("invalid_input", "Supplied plan not found in list of all available plans!");			
		}
			
		//If no explicit phase defined, nothing further to do.
		if(u_phase == null) return null;
		
		Plan plan = planManagers.get(0).getPlan();
	
		//Ensure our phase is found in the plan.
		List<Phase> phaseList = plan.getChildren().stream()
								.filter(phases -> phases.getName().equalsIgnoreCase(u_phase))
								.collect(Collectors.toList());
		
		//Ensure we found our phase.
		if(phaseList.size() != 1)
		{
			JSONObject outcome = new JSONObject();
			return outcome.put("invalid_input", "Supplied phase not found in set of possible phases with supplied plan!");			
		}
			
		//If no explicit step defined, nothing further to do.
		if(u_step == null) return null;
		
		Phase phase = phaseList.get(0);
		List<Step> stepList = phase.getChildren().stream() 
									.filter(steps -> steps.getName().equalsIgnoreCase(u_step))
									.collect(Collectors.toList());
	
		//Ensure we found our phase.
		if(stepList.size() != 1)
		{
			JSONObject outcome = new JSONObject();
			return outcome.put("invalid_input", "Supplied step not found in set of possible steps with supplied plan and phase!");			
		}
		
		//Successfully found plan, phase and step.
		return null;
	}
	
	public Response getJson(@QueryParam("plan") String u_plan,
							 @QueryParam("phase") String u_phase,
							 @QueryParam("step") String u_step,
							 @QueryParam("sync") boolean u_sync) {

		//Validate plan/phase/step if provided.
		if(u_plan != null || u_phase != null || u_step != null)
		{
			JSONObject validationOutcome = getValidationErrorResponse(u_plan, u_phase, u_step);
			if(validationOutcome != null)
				return ResponseUtils.jsonOkResponse(validationOutcome);
		}
	
		//At this point we're either returning the entire plans tree or
		//pruning it down to a plan/phase/step which has been validated.
	
		JSONObject response = new JSONObject();
		response.put("service-topology", generateServiceTopology());
		response.put("service-status", generateServiceStatus(u_plan, u_phase, u_step));
		
		return ResponseUtils.jsonOkResponse(response);
	}
}