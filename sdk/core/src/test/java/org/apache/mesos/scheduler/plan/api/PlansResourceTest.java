package org.apache.mesos.scheduler.plan.api;

import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class PlansResourceTest {
    @Mock private Plan mockPlan;
    @Mock private Phase mockPhase;
    @Mock private Block mockBlock;

    private PlanManager planManager;
    private final UUID phaseId = UUID.randomUUID();
    private final UUID blockId = UUID.randomUUID();
    private static final Strategy<Phase> strategy = new SerialStrategy<>();

    private static final String planName = "test-plan-manager";
    private PlansResource resource;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPhase.getId()).thenReturn(phaseId);
        when(mockBlock.getId()).thenReturn(blockId);
        when(mockPhase.getChildren()).thenReturn(Arrays.asList(mockBlock));
        when(mockPlan.getChildren()).thenReturn(Arrays.asList(mockPhase));
        when(mockPlan.getStrategy()).thenReturn(strategy);
        planManager = new DefaultPlanManager(mockPlan);
        Map<String, PlanManager> planManagers = new HashMap<>();
        planManagers.put("test-plan-manager", planManager);
        resource = new PlansResource(planManagers);
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
        Response response = resource.forceCompleteCommand(planName, phaseId.toString(), blockId.toString());
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
        Response response = resource.restartCommand(planName, phaseId.toString(), blockId.toString());
        assertTrue(response.getEntity() instanceof CommandResultInfo);

        CommandResultInfo commandResultInfo = (CommandResultInfo) response.getEntity();
        assertTrue(commandResultInfo.getMessage().contains("restart"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRestartInvalidId() {
        resource.restartCommand(planName, "aoeu", "asdf");
    }
}
