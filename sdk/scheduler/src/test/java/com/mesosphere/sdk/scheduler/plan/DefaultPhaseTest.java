package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.plan.strategy.CanaryStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class DefaultPhaseTest {

    @Test
    public void testGetStatus() {
        Step step1 = Mockito.mock(DeploymentStep.class);
        Step step2 = Mockito.mock(DeploymentStep.class);
        List<Step> steps = Arrays.asList(step1, step2);

        final DefaultPhase serialPhase = new DefaultPhase(
                "serial-phase",
                steps,
                new SerialStrategy<>(),
                Collections.emptyList());

        when(step1.getPodInstanceRequirement()).thenReturn(Optional.empty());
        when(step2.getPodInstanceRequirement()).thenReturn(Optional.empty());

        when(step1.getStatus()).thenReturn(Status.PENDING);
        when(step2.getStatus()).thenReturn(Status.WAITING);

        Assert.assertEquals(Status.PENDING, serialPhase.getStatus());

        when(step1.getStatus()).thenReturn(Status.WAITING);
        when(step2.getStatus()).thenReturn(Status.WAITING);

        final DefaultPhase canaryPhase = new DefaultPhase(
                "canary-phase",
                steps,
                new CanaryStrategy(new SerialStrategy<>(), steps),
                Collections.emptyList());

        Assert.assertEquals(Status.WAITING, canaryPhase.getStatus());
    }
}
