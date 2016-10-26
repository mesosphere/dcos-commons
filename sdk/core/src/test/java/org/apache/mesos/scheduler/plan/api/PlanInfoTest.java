package org.apache.mesos.scheduler.plan.api;

import org.apache.mesos.scheduler.plan.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class PlanInfoTest {

    @Mock Block mockBlock0;
    @Mock Block mockBlock1;
    @Mock Phase mockPhase0; // 2 blocks
    @Mock Phase mockPhase1; // no blocks
    @Mock
    Plan mockPlan; // 2 phases
    @Mock
    PlanManager mockPlanManager;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * This also effectively tests:
     * - {@link PhaseInfo#forPhase(Phase, PlanManager)}
     * - {@link BlockInfo#forBlock(Block, PlanManager)}.
     */
    @Test
    public void testForPlan() {

        // block calls within BlockInfo.forBlock(), against block 0 and block 1

        UUID block0Id = UUID.randomUUID();
        when(mockBlock0.getId()).thenReturn(block0Id);
        when(mockBlock0.isPending()).thenReturn(true);
        String block0Name = "block-0";
        when(mockBlock0.getName()).thenReturn(block0Name);
        String block0Message = "hi";
        when(mockBlock0.getMessage()).thenReturn(block0Message);
        when(mockBlock0.getStatus()).thenReturn(Status.PENDING);

        UUID block1Id = UUID.randomUUID();
        when(mockBlock1.getId()).thenReturn(block1Id);
        // no explicit status response: produce Status.ERROR
        String block1Name = "block-1";
        when(mockBlock1.getName()).thenReturn(block1Name);
        String block1Message = "hey";
        when(mockBlock1.getMessage()).thenReturn(block1Message);
        when(mockBlock1.getStatus()).thenReturn(Status.ERROR);

        // phase calls within PhaseInfo.forPhase(), against phase 0 and phase 1

        UUID phase0Id = UUID.randomUUID();
        when(mockPhase0.getId()).thenReturn(phase0Id);
        String phase0Name = "phase-0";
        when(mockPhase0.getName()).thenReturn(phase0Name);
        Status phase0Status = Status.PENDING;
        when(mockPhase0.getStatus()).thenReturn(phase0Status);
        // must use thenAnswer instead of thenReturn to work around java typing of "? extends Block"
        when(mockPhase0.getChildren()).thenReturn(Arrays.asList(mockBlock0, mockBlock1));

        UUID phase1Id = UUID.randomUUID();
        when(mockPhase1.getId()).thenReturn(phase1Id);
        String phase1Name = "phase-1";
        when(mockPhase1.getName()).thenReturn(phase1Name);
        Status phase1Status = Status.COMPLETE;
        when(mockPhase1.getStatus()).thenReturn(phase1Status);
        when(mockPhase1.getChildren()).thenReturn(new ArrayList<>());

        when(mockPlan.getChildren()).thenReturn(Arrays.asList(mockPhase0, mockPhase1));

        List<String> stageErrors = Arrays.asList("err0", "err1");
        when(mockPlan.getErrors()).thenReturn(stageErrors);

        when(mockPlanManager.getPlan()).thenReturn(mockPlan);
        when(mockPlan.getStatus()).thenReturn(Status.WAITING);

        PlanInfo planInfo = PlanInfo.forPlan(mockPlanManager);

        assertEquals(stageErrors, planInfo.getErrors());
        assertEquals(Status.WAITING, planInfo.getStatus());

        // phase 0 + 2 blocks
        PhaseInfo phaseInfo = planInfo.getPhases().get(0);
        assertEquals(phase0Id.toString(), phaseInfo.getId());
        assertEquals(phase0Name, phaseInfo.getName());
        assertEquals(phase0Status, phaseInfo.getStatus());
        assertEquals(2, phaseInfo.getBlocks().size());

        BlockInfo blockInfo = phaseInfo.getBlocks().get(0);
        assertEquals(block0Id.toString(), blockInfo.getId());
        assertEquals(block0Message, blockInfo.getMessage());
        assertEquals(block0Name, blockInfo.getName());
        assertEquals(Status.PENDING, blockInfo.getStatus());

        blockInfo = phaseInfo.getBlocks().get(1);
        assertEquals(block1Id.toString(), blockInfo.getId());
        assertEquals(block1Message, blockInfo.getMessage());
        assertEquals(block1Name, blockInfo.getName());
        assertEquals(Status.ERROR, blockInfo.getStatus());

        // phase 1 + 0 blocks
        phaseInfo = planInfo.getPhases().get(1);
        assertEquals(phase1Id.toString(), phaseInfo.getId());
        assertEquals(phase1Name, phaseInfo.getName());
        assertEquals(phase1Status, phaseInfo.getStatus());
        assertEquals(0, phaseInfo.getBlocks().size());

        // exercise equals/hashCode while we're at it:
        assertTrue(planInfo.equals(planInfo));
        assertEquals(planInfo.hashCode(), planInfo.hashCode());
        assertEquals(planInfo.toString(), planInfo.toString());
        assertTrue(phaseInfo.equals(phaseInfo));
        assertEquals(phaseInfo.hashCode(), phaseInfo.hashCode());
        assertEquals(phaseInfo.toString(), phaseInfo.toString());
        assertTrue(blockInfo.equals(blockInfo));
        assertEquals(blockInfo.hashCode(), blockInfo.hashCode());
        assertEquals(blockInfo.toString(), blockInfo.toString());
    }
}
