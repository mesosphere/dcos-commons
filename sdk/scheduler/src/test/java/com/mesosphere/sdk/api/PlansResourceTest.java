package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.PlansResource.CommandResultInfo;
import com.mesosphere.sdk.api.types.PlanInfo;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class PlansResourceTest {
    @Mock private Plan mockPlan;
    @Mock private Phase mockPhase;
    @Mock private Step mockStep;
    @Mock private PlanScheduler planScheduler;

    private PlanManager planManager;
    private final UUID phaseId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();
    private static final Strategy<Phase> strategy = new SerialStrategy<>();

    private static final String planName = "test-plan-manager";
    private PlansResource resource;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPhase.getId()).thenReturn(phaseId);
        when(mockStep.getId()).thenReturn(stepId);
        when(mockPhase.getChildren()).thenReturn(Arrays.asList(mockStep));
        when(mockPlan.getChildren()).thenReturn(Arrays.asList(mockPhase));
        when(mockPlan.getStrategy()).thenReturn(strategy);
        when(mockPlan.getName()).thenReturn(planName);
        planManager = new DefaultPlanManager(mockPlan);
        resource = new PlansResource(new DefaultPlanCoordinator(Arrays.asList(planManager), planScheduler));
    }

    @Test
    public void testFullInfoComplete() {
        when(mockPlan.isComplete()).thenReturn(true);
        Response response = resource.getPlanInfo(planName);
        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof PlanInfo);
    }

    @Test
    public void testFullInfoIncomplete() {
        when(mockPlan.isComplete()).thenReturn(false);
        Response response = resource.getPlanInfo(planName);
        assertEquals(503, response.getStatus());
        assertTrue(response.getEntity() instanceof PlanInfo);
    }

    @Test
    public void testContinue() {
        Response response = resource.continueCommand(planName);
        assertTrue(response.getEntity() instanceof CommandResultInfo);

        CommandResultInfo commandResultInfo = (CommandResultInfo) response.getEntity();
        assertTrue(commandResultInfo.getMessage().contains("continue"));
    }

    @Test
    public void testInterrupt() {
        Response response = resource.interruptCommand(planName);
        assertTrue(response.getEntity() instanceof CommandResultInfo);

        CommandResultInfo commandResultInfo = (CommandResultInfo) response.getEntity();
        assertTrue(commandResultInfo.getMessage().contains("interrupt"));
    }

    @Test
    public void testForceComplete() {
        Response response = resource.forceCompleteCommand(planName, phaseId.toString(), stepId.toString());
        assertTrue(response.getEntity() instanceof CommandResultInfo);

        CommandResultInfo commandResultInfo = (CommandResultInfo) response.getEntity();
        assertTrue(commandResultInfo.getMessage().contains("forceComplete"));
    }

    @Test
    public void testForceCompleteInvalidId() {
        Response response = resource.forceCompleteCommand(
                planName,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        assertEquals(PlansResource.PLAN_ELEMENT_NOT_FOUND_RESPONSE, response);
    }

    @Test
    public void testRestart() {
        Response response = resource.restartCommand(planName, phaseId.toString(), stepId.toString());
        assertTrue(response.getEntity() instanceof CommandResultInfo);

        CommandResultInfo commandResultInfo = (CommandResultInfo) response.getEntity();
        assertTrue(commandResultInfo.getMessage().contains("restart"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRestartInvalidId() {
        resource.restartCommand(planName, "aoeu", "asdf");
    }

    @Test
    public void testStart() {
        Response response = resource.startPlan(planName);
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
    }

    @Test
    public void testStartInvalid() {
        Response response = resource.startPlan("bad-plan");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testStop() {
        Response response = resource.stopPlan(planName);
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
    }

    @Test
    public void testStopInvalid() {
        Response response = resource.stopPlan("bad-plan");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testRestartPlan() {
        Response response = resource.restartCommand(planName, null, null);
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
    }

    @Test
    public void testRestartPlanIvalid() {
        Response response = resource.restartCommand("bad-plan", null, null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }
}
