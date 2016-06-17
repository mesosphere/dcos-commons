package org.apache.mesos.scheduler.plan.api;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Stage;
import org.apache.mesos.scheduler.plan.StageManager;
import org.apache.mesos.scheduler.plan.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class StageInfoTest {

    @Mock Block mockBlock0;
    @Mock Block mockBlock1;
    @Mock Phase mockPhase0; // 2 blocks
    @Mock Phase mockPhase1; // no blocks
    @Mock Stage mockStage; // 2 phases
    @Mock StageManager mockStageManager;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * This also effectively tests:
     * - {@link PhaseInfo#forPhase(Phase, StageManager)}
     * - {@link BlockInfo#forBlock(Block, StageManager)}.
     */
    @Test
    public void testForStage() {

        // block calls within BlockInfo.forBlock(), against block 0 and block 1

        UUID block0Id = UUID.randomUUID();
        when(mockBlock0.getId()).thenReturn(block0Id);
        when(mockBlock0.isPending()).thenReturn(true);
        String block0Name = "block-0";
        when(mockBlock0.getName()).thenReturn(block0Name);
        String block0Message = "hi";
        when(mockBlock0.getMessage()).thenReturn(block0Message);
        when(mockStageManager.hasDecisionPoint(mockBlock0)).thenReturn(false);

        UUID block1Id = UUID.randomUUID();
        when(mockBlock1.getId()).thenReturn(block1Id);
        // no explicit status response: produce Status.Error
        String block1Name = "block-1";
        when(mockBlock1.getName()).thenReturn(block1Name);
        String block1Message = "hey";
        when(mockBlock1.getMessage()).thenReturn(block1Message);
        when(mockStageManager.hasDecisionPoint(mockBlock1)).thenReturn(true);

        // phase calls within PhaseInfo.forPhase(), against phase 0 and phase 1

        UUID phase0Id = UUID.randomUUID();
        when(mockPhase0.getId()).thenReturn(phase0Id);
        String phase0Name = "phase-0";
        when(mockPhase0.getName()).thenReturn(phase0Name);
        Status phase0Status = Status.Pending;
        when(mockStageManager.getPhaseStatus(phase0Id)).thenReturn(phase0Status);
        // must use thenAnswer instead of thenReturn to work around java typing of "? extends Block"
        when(mockPhase0.getBlocks()).thenAnswer(new Answer<List<? extends Block>>() {
            @Override
            public List<? extends Block> answer(InvocationOnMock invocation)
                    throws Throwable {
                return Arrays.asList(mockBlock0, mockBlock1);
            }
        });

        UUID phase1Id = UUID.randomUUID();
        when(mockPhase1.getId()).thenReturn(phase1Id);
        String phase1Name = "phase-1";
        when(mockPhase1.getName()).thenReturn(phase1Name);
        Status phase1Status = Status.Complete;
        when(mockStageManager.getPhaseStatus(phase1Id)).thenReturn(phase1Status);
        when(mockPhase1.getBlocks()).thenReturn(new ArrayList<>());

        // stage calls within StageInfo.forStage()

        // must use thenAnswer instead of thenReturn to work around java typing of "? extends Block"
        when(mockStage.getPhases()).thenAnswer(new Answer<List<? extends Phase>>() {
            @Override
            public List<? extends Phase> answer(InvocationOnMock invocation)
                    throws Throwable {
                return Arrays.asList(mockPhase0, mockPhase1);
            }
        });
        List<String> stageErrors = Arrays.asList("err0", "err1");
        when(mockStage.getErrors()).thenReturn(stageErrors);

        when(mockStageManager.getStage()).thenReturn(mockStage);
        when(mockStageManager.getStatus()).thenReturn(Status.Waiting);


        StageInfo stageInfo = StageInfo.forStage(mockStageManager);


        assertEquals(stageErrors, stageInfo.getErrors());
        assertEquals(Status.Waiting, stageInfo.getStatus());

        // phase 0 + 2 blocks
        PhaseInfo phaseInfo = stageInfo.getPhases().get(0);
        assertEquals(phase0Id.toString(), phaseInfo.getId());
        assertEquals(phase0Name, phaseInfo.getName());
        assertEquals(phase0Status, phaseInfo.getStatus());
        assertEquals(2, phaseInfo.getBlocks().size());

        BlockInfo blockInfo = phaseInfo.getBlocks().get(0);
        assertEquals(false, blockInfo.getHasDecisionPoint());
        assertEquals(block0Id.toString(), blockInfo.getId());
        assertEquals(block0Message, blockInfo.getMessage());
        assertEquals(block0Name, blockInfo.getName());
        assertEquals(Status.Pending, blockInfo.getStatus());

        blockInfo = phaseInfo.getBlocks().get(1);
        assertEquals(true, blockInfo.getHasDecisionPoint());
        assertEquals(block1Id.toString(), blockInfo.getId());
        assertEquals(block1Message, blockInfo.getMessage());
        assertEquals(block1Name, blockInfo.getName());
        assertEquals(Status.Error, blockInfo.getStatus());

        // phase 1 + 0 blocks
        phaseInfo = stageInfo.getPhases().get(1);
        assertEquals(phase1Id.toString(), phaseInfo.getId());
        assertEquals(phase1Name, phaseInfo.getName());
        assertEquals(phase1Status, phaseInfo.getStatus());
        assertEquals(0, phaseInfo.getBlocks().size());

        // exercise equals/hashCode while we're at it:
        assertTrue(stageInfo.equals(stageInfo));
        assertEquals(stageInfo.hashCode(), stageInfo.hashCode());
        assertEquals(stageInfo.toString(), stageInfo.toString());
        assertTrue(phaseInfo.equals(phaseInfo));
        assertEquals(phaseInfo.hashCode(), phaseInfo.hashCode());
        assertEquals(phaseInfo.toString(), phaseInfo.toString());
        assertTrue(blockInfo.equals(blockInfo));
        assertEquals(blockInfo.hashCode(), blockInfo.hashCode());
        assertEquals(blockInfo.toString(), blockInfo.toString());
    }
}
