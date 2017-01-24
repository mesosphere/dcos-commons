package com.mesosphere.sdk.kafka.api;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/v1")
@Produces("application/json")
public class InterruptProceed {
    protected static final Logger LOGGER = LoggerFactory.getLogger(InterruptProceed.class);
    private PlanManager planManager;

    public InterruptProceed(PlanManager planManager) {
        this.planManager = planManager;
    }

    @POST
    @Path("/continue")
    public Response continueCommand() {
        try {
            List<Phase> phases = planManager.getPlan().getChildren().stream()
                    .filter(p -> p.getName().toString().equals("kafka"))
                    .collect(Collectors.toList());
            phases.forEach(p -> p.getStrategy().proceed());
            LOGGER.info("received to process interrupt request");
            return Response.ok(new JSONObject().put("Result","Received cmd: continue").toString(),
                    MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Failed to process interrupt request: " + e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/interrupt")
    public Response interruptCommand() {
        try {
            List<Phase> phases = planManager.getPlan().getChildren().stream()
                    .filter(p -> p.getName().toString().equals("kafka"))
                    .collect(Collectors.toList());
            phases.forEach(p -> p.getStrategy().interrupt());
            LOGGER.info("received to process continue request");
            return Response.ok(new JSONObject().put("Result","Received cmd: interrupt").toString(),
                    MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Failed to process continue request: " + e);
            return Response.serverError().build();
        }
    }
}
