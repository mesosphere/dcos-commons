package org.apache.mesos.scheduler.plan.api;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.mesos.scheduler.plan.Stage;
import org.apache.mesos.scheduler.plan.StageManager;
import org.apache.mesos.scheduler.plan.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.UUID;
import javax.ws.rs.core.Response;

public class StageResourceTest {
    @Mock private Stage mockStage;
    @Mock private StageManager mockStageManager;

    private StageResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        resource = new StageResource(mockStageManager);
    }

    @Test
    public void testGetStatus() {
        // just do the bare minimum. see CurrentlyActiveInfoTests for more thorough tests.
        when(mockStageManager.getCurrentBlock()).thenReturn(null);
        when(mockStageManager.getCurrentPhase()).thenReturn(null);
        when(mockStageManager.getStage()).thenReturn(mockStage);
        when(mockStageManager.getStatus()).thenReturn(Status.COMPLETE);
        when(mockStage.getPhases()).thenReturn(new ArrayList<>());
        when(mockStage.getErrors()).thenReturn(new ArrayList<>());

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
        when(mockStageManager.isComplete()).thenReturn(true);
        // just do the bare minimum. see StageInfoTests for more thorough tests.
        when(mockStageManager.getStage()).thenReturn(mockStage);
        when(mockStage.getPhases()).thenReturn(new ArrayList<>());
        when(mockStage.getErrors()).thenReturn(new ArrayList<>());
        when(mockStageManager.getStatus()).thenReturn(Status.PENDING);

        Response response = resource.getFullInfo();

        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof StageInfo);
    }

    @Test
    public void testFullInfoIncomplete() {
        when(mockStageManager.isComplete()).thenReturn(false);
        // just do the bare minimum. see StageInfoTests for more thorough tests.
        when(mockStageManager.getStage()).thenReturn(mockStage);
        when(mockStage.getPhases()).thenReturn(new ArrayList<>());
        when(mockStage.getErrors()).thenReturn(new ArrayList<>());
        when(mockStageManager.getStatus()).thenReturn(Status.PENDING);

        Response response = resource.getFullInfo();

        assertEquals(503, response.getStatus());
        assertTrue(response.getEntity() instanceof StageInfo);
    }

    @Test
    public void testContinue() {
        assertTrue(resource.continueCommand().getMessage().contains("continue"));
        verify(mockStageManager).proceed();
    }

    @Test
    public void testInterrupt() {
        assertTrue(resource.interruptCommand().getMessage().contains("interrupt"));
        verify(mockStageManager).interrupt();
    }

    @Test
    public void testForceComplete() {
        UUID phaseId = UUID.randomUUID(), blockId = UUID.randomUUID();
        assertTrue(resource.forceCompleteCommand(phaseId.toString(), blockId.toString())
                .getMessage().contains("forceComplete"));
        verify(mockStageManager).forceComplete(phaseId, blockId);
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
        verify(mockStageManager).restart(phaseId, blockId);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRestartInvalidId() {
        resource.restartCommand("aoeu", "asdf");
    }
}
