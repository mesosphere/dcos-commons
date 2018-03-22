package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.http.types.PlanInfo;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.StatusType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static com.mesosphere.sdk.http.ResponseUtils.alreadyReportedResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class PlansQueriesTest {
    @Mock private Plan mockPlan;
    @Mock private Strategy<Phase> mockPlanStrategy;
    @Mock private Phase mockPhase;
    @Mock private Strategy<Step> mockPhaseStrategy;
    @Mock private Step mockStep;

    private static final UUID stepId = UUID.randomUUID();
    private static final String stepName = "step-name";
    private static final String unknownStepName = "unknown-step";

    private static final UUID phaseId = UUID.randomUUID();
    private static final String phaseName = "phase-name";
    private static final String unknownPhaseName = "unknown-phase";

    private static final String planName = "test-plan";

    private Collection<PlanManager> planManagers;

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

        planManagers = Arrays.asList(DefaultPlanManager.createInterrupted(mockPlan));
        verify(mockPlan).interrupt(); // invoked by DefaultPlanManager
    }

    @Test
    public void testList() {
        when(mockPlan.isComplete()).thenReturn(true);
        Response response = PlansQueries.list(planManagers);
        assertEquals(200, response.getStatus());
        assertEquals(String.format("[\"%s\"]", planName), response.getEntity().toString());
    }

    @Test
    public void testFullInfoComplete() {
        when(mockPlan.isComplete()).thenReturn(true);
        Response response = PlansQueries.get(planManagers, planName);
        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof PlanInfo);
    }

    @Test
    public void testFullInfoError() {
        when(mockPlan.hasErrors()).thenReturn(true);
        Response response = PlansQueries.get(planManagers, planName);
        assertEquals(417, response.getStatus());
        assertTrue(response.getEntity() instanceof PlanInfo);
    }

    @Test
    public void testFullInfoErrorEvenIfComplete() {
        when(mockPlan.isComplete()).thenReturn(true);
        when(mockPlan.hasErrors()).thenReturn(true);
        Response response = PlansQueries.get(planManagers, planName);
        assertEquals(417, response.getStatus());
        assertTrue(response.getEntity() instanceof PlanInfo);
    }

    @Test
    public void testFullInfoIncomplete() {
        when(mockPlan.isComplete()).thenReturn(false);
        Response response = PlansQueries.get(planManagers, planName);
        assertEquals(202, response.getStatus());
        assertTrue(response.getEntity() instanceof PlanInfo);
    }

    @Test
    public void testFullInfoUnknownName() {
        when(mockPlan.isComplete()).thenReturn(false);
        Response response = PlansQueries.get(planManagers, "bad-name");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testContinue() {
        Response response = PlansQueries.continuePlan(planManagers, planName, null);
        validateCommandResult(response, "continue");
        verify(mockPlan).proceed();

        response = PlansQueries.continuePlan(planManagers, planName, phaseId.toString());
        validateCommandResult(response, "continue");
        verify(mockPhase).proceed();

        response = PlansQueries.continuePlan(planManagers, planName, phaseName);
        validateCommandResult(response, "continue");
        verify(mockPhase, times(2)).proceed();
    }

    @Test
    public void testContinueUnknownId() {
        Response response = PlansQueries.continuePlan(planManagers, "bad-name", null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = PlansQueries.continuePlan(planManagers, planName, "bad-name");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = PlansQueries.continuePlan(planManagers, "bad-name", phaseName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testContinueAlreadyInProgress() {
        StatusType expectedStatus = alreadyReportedResponse().getStatusInfo();

        when(mockPlan.isRunning()).thenReturn(true);
        when(mockPhase.isRunning()).thenReturn(true);
        when(mockStep.isRunning()).thenReturn(true);

        Response response = PlansQueries.continuePlan(planManagers, planName, null);
        assertTrue(response.getStatusInfo().equals(expectedStatus));

        response = PlansQueries.continuePlan(planManagers, planName, phaseName);
        assertTrue(response.getStatusInfo().equals(expectedStatus));
    }

    @Test
    public void testContinueAlreadyCompleted() {
        StatusType expectedStatus = alreadyReportedResponse().getStatusInfo();

        when(mockPlan.isComplete()).thenReturn(true);
        when(mockPhase.isComplete()).thenReturn(true);
        when(mockStep.isComplete()).thenReturn(true);

        Response response = PlansQueries.continuePlan(planManagers, planName, null);
        assertTrue(response.getStatusInfo().equals(expectedStatus));

        response = PlansQueries.continuePlan(planManagers, planName, phaseName);
        assertTrue(response.getStatusInfo().equals(expectedStatus));
    }

    @Test
    public void testInterrupt() {
        Response response = PlansQueries.interrupt(planManagers, planName, null);
        validateCommandResult(response, "interrupt");
        verify(mockPlan, times(2)).interrupt(); // already called once by DefaultPlanManager constructor

        response = PlansQueries.interrupt(planManagers, planName, phaseId.toString());
        validateCommandResult(response, "interrupt");
        verify(mockPhaseStrategy).interrupt();

        response = PlansQueries.interrupt(planManagers, planName, phaseName);
        validateCommandResult(response, "interrupt");
        verify(mockPhaseStrategy, times(2)).interrupt();
    }

    @Test
    public void testInterruptUnknownId() {
        Response response = PlansQueries.interrupt(planManagers, "bad-name", null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = PlansQueries.interrupt(planManagers, planName, "bad-name");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = PlansQueries.interrupt(planManagers, "bad-name", phaseName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testInterruptAlreadyInterrupted() {
        StatusType expectedStatus = alreadyReportedResponse().getStatusInfo();

        when(mockPlan.isInterrupted()).thenReturn(true);
        when(mockPhase.isInterrupted()).thenReturn(false);

        Response response = PlansQueries.interrupt(planManagers, planName, null);
        assertTrue(response.getStatusInfo().equals(expectedStatus));

        when(mockPlan.isInterrupted()).thenReturn(false);
        when(mockPhase.isInterrupted()).thenReturn(true);
        response = PlansQueries.interrupt(planManagers, planName, phaseName);
        assertTrue(response.getStatusInfo().equals(expectedStatus));
    }

    @Test
    public void testInterruptAlreadyCompleted() {
        StatusType expectedStatus = alreadyReportedResponse().getStatusInfo();

        when(mockPlan.isComplete()).thenReturn(true);

        Response response = PlansQueries.interrupt(planManagers, planName, null);
        assertTrue(response.getStatusInfo().equals(expectedStatus));

        when(mockPlan.isComplete()).thenReturn(false);
        when(mockPhase.isComplete()).thenReturn(true);
        response = PlansQueries.interrupt(planManagers, planName, phaseName);
        assertTrue(response.getStatusInfo().equals(expectedStatus));
    }

    @Test
    public void testForceComplete() {
        Response response = PlansQueries.forceComplete(planManagers, planName, phaseId.toString(), stepId.toString());
        validateCommandResult(response, "forceComplete");
        verify(mockStep).forceComplete();

        response = PlansQueries.forceComplete(planManagers, planName, phaseName, stepName);
        validateCommandResult(response, "forceComplete");
        verify(mockStep, times(2)).forceComplete();
    }

    @Test
    public void testForceCompleteUnknownId() {
        Response response = PlansQueries.forceComplete(planManagers, "bad-name", phaseName, stepName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
        verifyZeroInteractions(mockStep);

        response = PlansQueries.forceComplete(planManagers, planName, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
        verifyZeroInteractions(mockStep);

        response = PlansQueries.forceComplete(planManagers, planName, unknownPhaseName, unknownStepName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
        verifyZeroInteractions(mockStep);
    }

    @Test
    public void testForceCompleteAlreadyCompleted() {
        StatusType expectedStatus = alreadyReportedResponse().getStatusInfo();

        when(mockStep.isComplete()).thenReturn(true);

        Response response = PlansQueries.forceComplete(planManagers, planName, phaseId.toString(), stepId.toString());
        assertTrue(response.getStatusInfo().equals(expectedStatus));
    }

    @Test
    public void testForceCompletePlan() {
        Response response = PlansQueries.forceComplete(planManagers, planName, null, null);
        validateCommandResult(response, "forceComplete");
    }

    @Test
    public void testForceCompletePhase() {
        Response response = PlansQueries.forceComplete(planManagers, planName, phaseName, null);
        validateCommandResult(response, "forceComplete");
    }

    @Test
    public void testFailForceCompletePlanAndStep() {
        Response response = PlansQueries.forceComplete(planManagers, planName, null, stepName);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testFailForceCompleteStep() {
        Response response = PlansQueries.forceComplete(planManagers, null, null, stepName);
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testFailForceCompleteNothing() {
        Response response = PlansQueries.forceComplete(planManagers, null, null, null);
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testRestart() {
        Response response = PlansQueries.restart(planManagers, planName, phaseId.toString(), stepId.toString());
        validateCommandResult(response, "restart");
        verify(mockStep).restart();
        verify(mockStep).proceed();

        response = PlansQueries.restart(planManagers, planName, phaseName, stepName);
        validateCommandResult(response, "restart");
        verify(mockStep, times(2)).restart();
        verify(mockStep, times(2)).proceed();
    }

    @Test
    public void testRestartUnknownId() {
        Response response = PlansQueries.restart(planManagers, "bad-name", phaseName, stepName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
        verifyZeroInteractions(mockStep);

        response = PlansQueries.restart(planManagers, planName, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
        verifyZeroInteractions(mockStep);

        response = PlansQueries.restart(planManagers, planName, unknownPhaseName, unknownStepName);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
        verifyZeroInteractions(mockStep);
    }

    @Test
    public void testStart() {
        when(mockPlan.isComplete()).thenReturn(false);
        Response response = PlansQueries.start(planManagers, planName, Collections.singletonMap("SOME_ENVVAR", "val"));
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verify(mockPlan, times(0)).restart();
        verify(mockPlan).proceed();
    }

    @Test
    public void testStartAlreadyStarted() {
        when(mockPlan.isComplete()).thenReturn(true);
        Response response = PlansQueries.start(planManagers, planName, Collections.emptyMap());
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verify(mockPlan).restart();
        verify(mockPlan).proceed();
    }

    @Test
    public void testStartInvalid() {
        Response response = PlansQueries.start(planManagers, "bad-plan", Collections.emptyMap());
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = PlansQueries.start(planManagers, planName, Collections.singletonMap("not-valid-envname", "val"));
        assertTrue(response.getStatusInfo().equals(Response.Status.BAD_REQUEST));
    }

    @Test
    public void testStop() {
        Response response = PlansQueries.stop(planManagers, planName);
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verify(mockPlan, times(2)).interrupt(); // already called once by DefaultPlanManager constructor
        verify(mockPlan).restart();
    }

    @Test
    public void testStopInvalid() {
        Response response = PlansQueries.stop(planManagers, "bad-plan");
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testRestartPlan() {
        Response response = PlansQueries.restart(planManagers, planName, null, null);
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verifyZeroInteractions(mockPhase);
        verify(mockPlan).restart();
        verify(mockPlan).proceed();
    }

    @Test
    public void testRestartPhase() {
        Response response = PlansQueries.restart(planManagers, planName, phaseName, null);
        assertTrue(response.getStatusInfo().equals(Response.Status.OK));
        verify(mockPhase).restart();
        verify(mockPhase).proceed();
    }

    @Test
    public void testRestartPhaseInvalid() {
        Response response = PlansQueries.restart(planManagers, planName, "bad-phase", null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));
    }

    @Test
    public void testRestartPlanInvalid() {
        Response response = PlansQueries.restart(planManagers, "bad-plan", null, null);
        assertTrue(response.getStatusInfo().equals(Response.Status.NOT_FOUND));

        response = PlansQueries.restart(planManagers, planName, null, "non-null");
        assertTrue(response.getStatusInfo().equals(Response.Status.BAD_REQUEST));
    }

    private static void validateCommandResult(Response response, String commandName) {
        String expectedPrefix = String.format("{\"message\": \"Received cmd: %s", commandName);
        assertTrue(response.getEntity().toString().startsWith(expectedPrefix));
        assertEquals(200, response.getStatus());
    }
}
