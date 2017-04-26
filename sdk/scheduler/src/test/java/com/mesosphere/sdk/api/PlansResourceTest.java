package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.types.PlanInfo;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class PlansResourceTest {
    @Mock private Plan mockPlan;
    @Mock private Strategy<Phase> mockPlanStrategy;
    @Mock private Phase mockPhase;
    @Mock private Strategy<Step> mockPhaseStrategy;
    @Mock private Step mockStep;
    @Mock private PlanScheduler planScheduler;

    private static final UUID stepId = UUID.randomUUID();
    private static final String stepName = "step-name";
    private static final String unknownStepName = "unknown-step";

    private static final UUID phaseId = UUID.randomUUID();
    private static final String phaseName = "phase-name";
    private static final String unknownPhaseName = "unknown-phase";

    private static final String planName = "test-plan";

    private PlansResource resource;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        when(mockStep.getId()).thenReturn(stepId);
        when(mockStep.getName()).thenReturn(stepName);

        when(mockPhase.getChildren()).thenReturn(Arrays.asList(mockStep));
        when(mockPhase.getStrategy()).thenReturn(mockPhaseStrategy);
        when(mockPhase.getId()).thenReturn(phaseId);
        when(mockPhase.getName()).thenReturn(phaseName);

        when(mockPlan.getChildren()).thenReturn(Arrays.asList(mockPhase));
        when(mockPlan.getStrategy()).thenReturn(mockPlanStrategy);
        when(mockPlan.getName()).thenReturn(planName);

        resource = new PlansResource(
                new DefaultPlanCoordinator(Arrays.asList(new DefaultPlanManager(mockPlan)), planScheduler));
        verify(mockPlan).interrupt(); // invoked by DefaultPlanManager
    }

    @Test
    public void testListPlans() {
        when(mockPlan.isComplete()).thenReturn(true);
        Response response = resource.listPlans();
        assertEquals(200, response.getStatus());
        assertEquals(String.format("[\"%s\"]", planName), response.getEntity().toString());
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
        assertEquals(202, response.getStatus());
        assertTrue(response.getEntity() instanceof PlanInfo);
    }

    @Test
    public void testFullInfoUnknownName() {
        when(mockPlan.isComplete()).thenReturn(false);
        Response response = resource.getPlanInfo("bad-name");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testContinue() {
        Response response = resource.continueCommand(planName, null);
        validateCommandResult(response, "continue");
        verify(mockPlan).proceed();

        response = resource.continueCommand(planName, phaseId.toString());
        validateCommandResult(response, "continue");
        verify(mockPhase).proceed();

        response = resource.continueCommand(planName, phaseName);
        validateCommandResult(response, "continue");
        verify(mockPhase, times(2)).proceed();
    }

    @Test
    public void testContinueUnknownId() {
        Response response = resource.continueCommand("bad-name", null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = resource.continueCommand(planName, "bad-name");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = resource.continueCommand("bad-name", phaseName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testInterrupt() {
        Response response = resource.interruptCommand(planName, null);
        validateCommandResult(response, "interrupt");
        verify(mockPlan, times(2)).interrupt(); // already called once by DefaultPlanManager constructor

        response = resource.interruptCommand(planName, phaseId.toString());
        validateCommandResult(response, "interrupt");
        verify(mockPhaseStrategy).interrupt();

        response = resource.interruptCommand(planName, phaseName);
        validateCommandResult(response, "interrupt");
        verify(mockPhaseStrategy, times(2)).interrupt();
    }

    @Test
    public void testInterruptUnknownId() {
        Response response = resource.interruptCommand("bad-name", null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = resource.interruptCommand(planName, "bad-name");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = resource.interruptCommand("bad-name", phaseName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testForceComplete() {
        Response response = resource.forceCompleteCommand(planName, phaseId.toString(), stepId.toString());
        validateCommandResult(response, "forceComplete");
        verify(mockStep).forceComplete();

        response = resource.forceCompleteCommand(planName, phaseName, stepName);
        validateCommandResult(response, "forceComplete");
        verify(mockStep, times(2)).forceComplete();
    }

    @Test
    public void testForceCompleteUnknownId() {
        Response response = resource.forceCompleteCommand("bad-name", phaseName, stepName);
        assertEquals(PlansResource.ELEMENT_NOT_FOUND_RESPONSE, response);
        verifyZeroInteractions(mockStep);

        response = resource.forceCompleteCommand(planName, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        assertEquals(PlansResource.ELEMENT_NOT_FOUND_RESPONSE, response);
        verifyZeroInteractions(mockStep);

        response = resource.forceCompleteCommand(planName, unknownPhaseName, unknownStepName);
        assertEquals(PlansResource.ELEMENT_NOT_FOUND_RESPONSE, response);
        verifyZeroInteractions(mockStep);
    }

    @Test
    public void testRestart() {
        Response response = resource.restartCommand(planName, phaseId.toString(), stepId.toString());
        validateCommandResult(response, "restart");
        verify(mockStep).restart();

        response = resource.restartCommand(planName, phaseName, stepName);
        validateCommandResult(response, "restart");
        verify(mockStep, times(2)).restart();
    }

    @Test
    public void testRestartUnknownId() {
        Response response = resource.restartCommand("bad-name", phaseName, stepName);
        assertEquals(PlansResource.ELEMENT_NOT_FOUND_RESPONSE, response);
        verifyZeroInteractions(mockStep);

        response = resource.restartCommand(planName, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        assertEquals(PlansResource.ELEMENT_NOT_FOUND_RESPONSE, response);
        verifyZeroInteractions(mockStep);

        response = resource.restartCommand(planName, unknownPhaseName, unknownStepName);
        assertEquals(PlansResource.ELEMENT_NOT_FOUND_RESPONSE, response);
        verifyZeroInteractions(mockStep);
    }

    @Test
    public void testStart() {
        when(mockPlan.isComplete()).thenReturn(false);
        Response response = resource.startPlan(planName, Collections.singletonMap("SOME_ENVVAR", "val"));
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verify(mockPlan, times(0)).restart();
        verify(mockPlan).proceed();
    }

    @Test
    public void testStartAlreadyStarted() {
        when(mockPlan.isComplete()).thenReturn(true);
        Response response = resource.startPlan(planName, Collections.emptyMap());
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verify(mockPlan).restart();
        verify(mockPlan).proceed();
    }

    @Test
    public void testStartInvalid() {
        Response response = resource.startPlan("bad-plan", Collections.emptyMap());
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = resource.startPlan(planName, Collections.singletonMap("not-valid-envname", "val"));
        assertTrue(response.getStatusInfo().equals(Response.Status.BAD_REQUEST));
    }

    @Test
    public void testStop() {
        Response response = resource.stopPlan(planName);
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verify(mockPlan, times(2)).interrupt(); // already called once by DefaultPlanManager constructor
        verify(mockPlan).restart();
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
        verifyZeroInteractions(mockPhase);
        verify(mockPlan).restart();
        verify(mockPlan).proceed();
    }

    @Test
    public void testRestartPlanInvalid() {
        Response response = resource.restartCommand("bad-plan", null, null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = resource.restartCommand(planName, "non-null", null);
        assertTrue(response.getStatusInfo().equals(Response.Status.BAD_REQUEST));

        response = resource.restartCommand(planName, null, "non-null");
        assertTrue(response.getStatusInfo().equals(Response.Status.BAD_REQUEST));
    }

    private static void validateCommandResult(Response response, String commandName) {
        assertEquals("{\"message\": \"Received cmd: " + commandName + "\"}", response.getEntity().toString());
    }
}
