package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.when;

public class DefaultPhaseTest {

    @Test
    public void testGetStatus() {
        Step step1 = Mockito.mock(DeploymentStep.class);
        Step step2 = Mockito.mock(DeploymentStep.class);

        final DefaultPhase phase = new DefaultPhase(
                "test-phase",
                Arrays.asList(step1, step2),
                new SerialStrategy<>(),
                Collections.emptyList());

        when(step1.getStatus()).thenReturn(Status.PENDING);
        when(step2.getStatus()).thenReturn(Status.WAITING);

        Assert.assertEquals(Status.PENDING, phase.getStatus());
    }
}
