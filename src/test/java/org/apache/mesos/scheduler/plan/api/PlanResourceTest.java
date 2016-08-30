package org.apache.mesos.scheduler.plan.api;

import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.PlanManager;
import org.apache.mesos.scheduler.plan.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlanResourceTest {
    @Mock private Plan mockPlan;
    @Mock private PlanManager mockPlanManager;

    private PlanResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        resource = new PlanResource(mockPlanManager);
    }

    @Test
    public void testGetStatus() {
        // just do the bare minimum. see CurrentlyActiveInfoTests for more thorough tests.
        when(mockPlanManager.getCurrentBlock()).thenReturn(null);
        when(mockPlanManager.getCurrentPhase()).thenReturn(null);
        when(mockPlanManager.getPlan()).thenReturn(mockPlan);
        when(mockPlanManager.getStatus()).thenReturn(Status.COMPLETE);
        when(mockPlanManager.getCurrentBlock()).thenReturn(Optional.empty());
        when(mockPlanManager.getCurrentPhase()).thenReturn(Optional.empty());
        when(mockPlan.getPhases()).thenReturn(new ArrayList<>());
        when(mockPlan.getErrors()).thenReturn(new ArrayList<>());

        CurrentlyActiveInfo activeInfo = resource.getStatus();

        assertNull(activeInfo.getBlock());
        assertNull(activeInfo.getPhaseStatus());
        CurrentlyActiveStageInfo stageInfo = activeInfo.getStageStatus();
        assertEquals(Status.COMPLETE, stageInfo.getStatus());
        assertEquals(Integer.valueOf(0), stageInfo.getPhaseCount());
        assertTrue(stageInfo.getErrors().isEmpty());
    }

    @Test
    public void testFullInfoComplete() {
        when(mockPlanManager.isComplete()).thenReturn(true);
        // just do the bare minimum. see StageInfoTests for more thorough tests.
        when(mockPlanManager.getPlan()).thenReturn(mockPlan);
        when(mockPlan.getPhases()).thenReturn(new ArrayList<>());
        when(mockPlan.getErrors()).thenReturn(new ArrayList<>());
        when(mockPlanManager.getStatus()).thenReturn(Status.PENDING);

        Response response = resource.getFullInfo();

        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof StageInfo);
    }

    @Test
    public void testFullInfoIncomplete() {
        when(mockPlanManager.isComplete()).thenReturn(false);
        // just do the bare minimum. see StageInfoTests for more thorough tests.
        when(mockPlanManager.getPlan()).thenReturn(mockPlan);
        when(mockPlan.getPhases()).thenReturn(new ArrayList<>());
        when(mockPlan.getErrors()).thenReturn(new ArrayList<>());
        when(mockPlanManager.getStatus()).thenReturn(Status.PENDING);

        Response response = resource.getFullInfo();

        assertEquals(503, response.getStatus());
        assertTrue(response.getEntity() instanceof StageInfo);
    }

    @Test
    public void testContinue() {
        assertTrue(resource.continueCommand().getMessage().contains("continue"));
        verify(mockPlanManager).proceed();
    }

    @Test
    public void testInterrupt() {
        assertTrue(resource.interruptCommand().getMessage().contains("interrupt"));
        verify(mockPlanManager).interrupt();
    }

    @Test
    public void testForceComplete() {
        UUID phaseId = UUID.randomUUID(), blockId = UUID.randomUUID();
        assertTrue(resource.forceCompleteCommand(phaseId.toString(), blockId.toString())
                .getMessage().contains("forceComplete"));
        verify(mockPlanManager).forceComplete(phaseId, blockId);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testForceCompleteInvalidId() {
        resource.forceCompleteCommand("aoeu", "asdf");
    }

    @Test
    public void testRestart() {
        UUID phaseId = UUID.randomUUID(), blockId = UUID.randomUUID();
        assertTrue(resource.restartCommand(phaseId.toString(), blockId.toString())
                .getMessage().contains("restart"));
        verify(mockPlanManager).restart(phaseId, blockId);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRestartInvalidId() {
        resource.restartCommand("aoeu", "asdf");
    }
}
