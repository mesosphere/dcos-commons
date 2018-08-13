package com.mesosphere.sdk.metrics;

import com.codahale.metrics.Gauge;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for {@link PlanReporter}.
 */
public class PlanReporterTest {

    @Test
    public void testPlanReporter() throws Exception {
        PlanManager manager1 = Mockito.mock(PlanManager.class);
        PlanManager manager2 = Mockito.mock(PlanManager.class);
        Plan plan1 = Mockito.mock(Plan.class);
        Plan plan2 = Mockito.mock(Plan.class);

        Mockito.when(manager1.getPlan()).thenReturn(plan1);
        Mockito.when(manager2.getPlan()).thenReturn(plan2);
        Mockito.when(plan1.getStatus()).thenReturn(Status.ERROR);
        Mockito.when(plan1.getName()).thenReturn("plan1");
        Mockito.when(plan2.getStatus()).thenReturn(Status.COMPLETE);
        Mockito.when(plan2.getName()).thenReturn("plan2");

        PlanReporter reporter = new PlanReporter(Optional.empty(), Arrays.asList(manager1, manager2), 10);
        while (!reporter.getHasScraped()) {
            Thread.sleep(5);
        }

        Map<String, Gauge> gauges =
                Metrics.getRegistry().getGauges((name, metric) -> name.startsWith("plan_status.plan"));
        Assert.assertEquals(2, gauges.size());
        Assert.assertEquals(-1, gauges.get("plan_status.plan1").getValue());
        Assert.assertEquals(0, gauges.get("plan_status.plan2").getValue());
    }
}
