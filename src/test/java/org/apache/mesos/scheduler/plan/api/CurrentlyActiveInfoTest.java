package org.apache.mesos.scheduler.plan.api;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.PlanManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CurrentlyActiveInfoTest {

    @Mock Block mockBlock0;
    @Mock Block mockBlock1;
    @Mock Phase mockPhase0; // 2 blocks
    @Mock Phase mockPhase1;
    @Mock
    Plan mockPlan; // 2 phases
    @Mock
    PlanManager mockPlanManager;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testForInactiveStage() {
        // must use thenAnswer instead of thenReturn to work around java typing of "? extends Block"
        when(mockPlan.getPhases()).thenAnswer(new Answer<List<? extends Phase>>() {
            @Override
            public List<? extends Phase> answer(InvocationOnMock invocation)
                    throws Throwable {
                return Arrays.asList(mockPhase0, mockPhase1);
            }
        });
        List<String> stageErrors = Arrays.asList("err0", "err1");
        when(mockPlan.getErrors()).thenReturn(stageErrors);

        when(mockPlanManager.getPlan()).thenReturn(mockPlan);
        when(mockPlanManager.getStatus()).thenReturn(Status.WAITING);

        CurrentlyActiveInfo activeInfo = CurrentlyActiveInfo.forStage(mockPlanManager);

        assertNull(activeInfo.getBlock());
        assertNull(activeInfo.getPhaseStatus());
        assertEquals(stageErrors, activeInfo.getStageStatus().getErrors());
        assertEquals(Integer.valueOf(2), activeInfo.getStageStatus().getPhaseCount());
        assertEquals(Status.WAITING, activeInfo.getStageStatus().getStatus());
    }

    /**
     * This also effectively tests:
     * - {@link BlockInfo#forBlock(Block, PlanManager)}
     * - {@link CurrentlyActivePhaseInfo#forPhase(Phase, PlanManager)}
     * - {@link CurrentlyActiveStageInfo#forStage(PlanManager)}.
     */
    @Test
    public void testForActiveStage() {

        // block calls within BlockInfo.forBlock(), against block 0
        UUID block0Id = UUID.randomUUID();
        when(mockBlock0.getId()).thenReturn(block0Id);
        when(mockBlock0.isPending()).thenReturn(true);
        String block0Name = "block-0";
        when(mockBlock0.getName()).thenReturn(block0Name);
        String block0Message = "hi";
        when(mockBlock0.getMessage()).thenReturn(block0Message);
        when(mockPlanManager.hasDecisionPoint(mockBlock0)).thenReturn(false);

        // phase calls within CurrentlyActivePhaseInfo.forPhase()

        UUID phase0Id = UUID.randomUUID();
        when(mockPhase0.getId()).thenReturn(phase0Id);
        String phase0Name = "phase-0";
        when(mockPhase0.getName()).thenReturn(phase0Name);
        Status phase0Status = Status.PENDING;
        when(mockPlanManager.getPhaseStatus(phase0Id)).thenReturn(phase0Status);
        // must use thenAnswer instead of thenReturn to work around java typing of "? extends Block"
        when(mockPhase0.getBlocks()).thenAnswer(new Answer<List<? extends Block>>() {
            @Override
            public List<? extends Block> answer(InvocationOnMock invocation)
                    throws Throwable {
                return Arrays.asList(mockBlock0, mockBlock1);
            }
        });

        // plan calls within CurrentlyActiveStageInfo.forStage()

        // must use thenAnswer instead of thenReturn to work around java typing of "? extends Block"
        when(mockPlan.getPhases()).thenAnswer(new Answer<List<? extends Phase>>() {
            @Override
            public List<? extends Phase> answer(InvocationOnMock invocation)
                    throws Throwable {
                return Arrays.asList(mockPhase0, mockPhase1);
            }
        });
        List<String> stageErrors = Arrays.asList("err0", "err1");
        when(mockPlan.getErrors()).thenReturn(stageErrors);

        when(mockPlanManager.getCurrentBlock()).thenReturn(mockBlock0);
        when(mockPlanManager.getCurrentPhase()).thenReturn(mockPhase0);
        when(mockPlanManager.getPlan()).thenReturn(mockPlan);
        when(mockPlanManager.getStatus()).thenReturn(Status.WAITING);


        CurrentlyActiveInfo activeInfo = CurrentlyActiveInfo.forStage(mockPlanManager);


        BlockInfo blockInfo = activeInfo.getBlock();
        assertEquals(false, blockInfo.getHasDecisionPoint());
        assertEquals(block0Id.toString(), blockInfo.getId());
        assertEquals(block0Message, blockInfo.getMessage());
        assertEquals(block0Name, blockInfo.getName());
        assertEquals(Status.PENDING, blockInfo.getStatus());

        CurrentlyActivePhaseInfo phaseInfo = activeInfo.getPhaseStatus();
        assertEquals(phase0Id.toString(), phaseInfo.getId());
        assertEquals(phase0Name, phaseInfo.getName());
        assertEquals(phase0Status, phaseInfo.getStatus());
        assertEquals(Integer.valueOf(2), phaseInfo.getBlockCount());

        CurrentlyActiveStageInfo stageInfo = activeInfo.getStageStatus();
        assertEquals(stageErrors, stageInfo.getErrors());
        assertEquals(Integer.valueOf(2), stageInfo.getPhaseCount());
        assertEquals(Status.WAITING, stageInfo.getStatus());

        // exercise equals/hashCode while we're at it:
        assertTrue(activeInfo.equals(activeInfo));
        assertEquals(activeInfo.hashCode(), activeInfo.hashCode());
        assertEquals(activeInfo.toString(), activeInfo.toString());
        assertTrue(blockInfo.equals(blockInfo));
        assertEquals(blockInfo.hashCode(), blockInfo.hashCode());
        assertEquals(blockInfo.toString(), blockInfo.toString());
        assertTrue(phaseInfo.equals(phaseInfo));
        assertEquals(phaseInfo.hashCode(), phaseInfo.hashCode());
        assertEquals(phaseInfo.toString(), phaseInfo.toString());
        assertTrue(stageInfo.equals(stageInfo));
        assertEquals(stageInfo.hashCode(), stageInfo.hashCode());
        assertEquals(stageInfo.toString(), stageInfo.toString());
    }
}
