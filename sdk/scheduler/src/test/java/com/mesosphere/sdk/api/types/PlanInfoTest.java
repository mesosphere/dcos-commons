package com.mesosphere.sdk.api.types;

import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
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

    @Mock Step mockStep0;
    @Mock Step mockStep1;
    @Mock Phase mockPhase0; // 2 steps
    @Mock Phase mockPhase1; // no steps
    @Mock Plan mockPlan; // 2 phases

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testForPlan() {

        // step calls within StepInfo.forStep(), against step 0 and step 1

        UUID step0Id = UUID.randomUUID();
        when(mockStep0.getId()).thenReturn(step0Id);
        String step0Name = "step-0";
        when(mockStep0.getName()).thenReturn(step0Name);
        String step0Message = "hi";
        when(mockStep0.getMessage()).thenReturn(step0Message);
        when(mockStep0.getDisplayStatus()).thenReturn("PENDING");

        UUID step1Id = UUID.randomUUID();
        when(mockStep1.getId()).thenReturn(step1Id);
        // no explicit status response: produce Status.ERROR
        String step1Name = "step-1";
        when(mockStep1.getName()).thenReturn(step1Name);
        String step1Message = "hey";
        when(mockStep1.getMessage()).thenReturn(step1Message);
        when(mockStep1.getDisplayStatus()).thenReturn("ERROR");

        // phase calls within PhaseInfo.forPhase(), against phase 0 and phase 1

        UUID phase0Id = UUID.randomUUID();
        when(mockPhase0.getId()).thenReturn(phase0Id);
        String phase0Name = "phase-0";
        when(mockPhase0.getName()).thenReturn(phase0Name);
        Status phase0Status = Status.PENDING;
        when(mockPhase0.getStatus()).thenReturn(phase0Status);
        when(mockPhase0.getStrategy()).thenReturn(new SerialStrategy<>());
        // must use thenAnswer instead of thenReturn to work around java typing of "? extends Step"
        when(mockPhase0.getChildren()).thenReturn(Arrays.asList(mockStep0, mockStep1));

        UUID phase1Id = UUID.randomUUID();
        when(mockPhase1.getId()).thenReturn(phase1Id);
        String phase1Name = "phase-1";
        when(mockPhase1.getName()).thenReturn(phase1Name);
        Status phase1Status = Status.COMPLETE;
        when(mockPhase1.getStatus()).thenReturn(phase1Status);
        when(mockPhase1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(mockPhase1.getChildren()).thenReturn(new ArrayList<>());

        when(mockPlan.getChildren()).thenReturn(Arrays.asList(mockPhase0, mockPhase1));

        List<String> stageErrors = Arrays.asList("err0", "err1");
        when(mockPlan.getErrors()).thenReturn(stageErrors);

        when(mockPlan.getStatus()).thenReturn(Status.WAITING);
        when(mockPlan.getStrategy()).thenReturn(new SerialStrategy<>());

        PlanInfo planInfo = PlanInfo.forPlan(mockPlan);

        assertEquals(stageErrors, planInfo.getErrors());
        assertEquals(Status.WAITING, planInfo.getStatus());

        // phase 0 + 2 steps
        PhaseInfo phaseInfo = planInfo.getPhases().get(0);
        assertEquals(phase0Id.toString(), phaseInfo.getId());
        assertEquals(phase0Name, phaseInfo.getName());
        assertEquals(phase0Status, phaseInfo.getStatus());
        assertEquals(2, phaseInfo.getSteps().size());

        StepInfo stepInfo = phaseInfo.getSteps().get(0);
        assertEquals(step0Id.toString(), stepInfo.getId());
        assertEquals(step0Message, stepInfo.getMessage());
        assertEquals(step0Name, stepInfo.getName());
        assertEquals("PENDING", stepInfo.getStatus());

        stepInfo = phaseInfo.getSteps().get(1);
        assertEquals(step1Id.toString(), stepInfo.getId());
        assertEquals(step1Message, stepInfo.getMessage());
        assertEquals(step1Name, stepInfo.getName());
        assertEquals("ERROR", stepInfo.getStatus());

        // phase 1 + 0 steps
        phaseInfo = planInfo.getPhases().get(1);
        assertEquals(phase1Id.toString(), phaseInfo.getId());
        assertEquals(phase1Name, phaseInfo.getName());
        assertEquals(phase1Status, phaseInfo.getStatus());
        assertEquals(0, phaseInfo.getSteps().size());

        // exercise equals/hashCode while we're at it:
        assertTrue(planInfo.equals(planInfo));
        assertEquals(planInfo.hashCode(), planInfo.hashCode());
        assertEquals(planInfo.toString(), planInfo.toString());
        assertTrue(phaseInfo.equals(phaseInfo));
        assertEquals(phaseInfo.hashCode(), phaseInfo.hashCode());
        assertEquals(phaseInfo.toString(), phaseInfo.toString());
        assertTrue(stepInfo.equals(stepInfo));
        assertEquals(stepInfo.hashCode(), stepInfo.hashCode());
        assertEquals(stepInfo.toString(), stepInfo.toString());
    }
}
