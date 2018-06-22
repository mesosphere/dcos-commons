package com.mesosphere.sdk.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

/**
 * This class tests the {@link Metrics} class.
 */
public class MetricsTest {

    @Test
    public void incrementReceivedOffers() {
        Counter counter = Metrics.getRegistry().counter(Metrics.RECEIVED_OFFERS);
        long val = counter.getCount();
        Metrics.incrementReceivedOffers(5);
        Assert.assertEquals(5, counter.getCount() - val);
    }

    @Test
    public void incrementProcessedOffers() {
        Counter counter = Metrics.getRegistry().counter(Metrics.PROCESSED_OFFERS);
        long val = counter.getCount();
        Metrics.incrementProcessedOffers(5);
        Assert.assertEquals(5, counter.getCount() - val);
    }

    @Test
    public void incrementProcessOffersDuration() {
        Timer timer = Metrics.getRegistry().timer(Metrics.PROCESS_OFFERS);
        long val = timer.getCount();
        Metrics.getProcessOffersDurationTimer().stop();
        Assert.assertEquals(1, timer.getCount() - val);
    }

    @Test
    public void incrementRevives() {
        Counter counter = Metrics.getRegistry().counter(Metrics.REVIVES);
        long val = counter.getCount();
        Metrics.incrementRevives();
        Assert.assertEquals(1, counter.getCount() - val);
    }

    @Test
    public void incrementReviveThrottles() {
        Counter counter = Metrics.getRegistry().counter(Metrics.REVIVE_THROTTLES);
        long val = counter.getCount();
        Metrics.incrementReviveThrottles();
        Assert.assertEquals(1, counter.getCount() - val);
    }

    @Test
    public void incrementDeclinesShort() {
        Counter counter = Metrics.getRegistry().counter(Metrics.DECLINE_SHORT);
        long val = counter.getCount();
        Metrics.incrementDeclinesShort(5);
        Assert.assertEquals(5, counter.getCount() - val);
    }

    @Test
    public void incrementDeclinesLong() {
        Counter counter = Metrics.getRegistry().counter(Metrics.DECLINE_LONG);
        long val = counter.getCount();
        Metrics.incrementDeclinesLong(5);
        Assert.assertEquals(5, counter.getCount() - val);
    }

    @Test
    public void taskStatuses() {
        Counter runningCounter = Metrics.getRegistry().counter("task_status.task_running");
        Counter lostCounter = Metrics.getRegistry().counter("task_status.task_lost");

        long runningVal = runningCounter.getCount();
        Metrics.record(Protos.TaskStatus.newBuilder()
                .setState(Protos.TaskState.TASK_RUNNING)
                .setTaskId(TestConstants.TASK_ID)
                .build());
        Assert.assertEquals(1, runningCounter.getCount() - runningVal);

        long lostVal = lostCounter.getCount();
        Metrics.record(Protos.TaskStatus.newBuilder()
                .setState(Protos.TaskState.TASK_LOST)
                .setTaskId(TestConstants.TASK_ID)
                .build());
        Assert.assertEquals(1, lostCounter.getCount() - lostVal);
    }

    @Test
    public void taskLaunches() throws Exception {
        OfferRecommendation realRecommendation = new LaunchOfferRecommendation(
                OfferTestUtils.getEmptyOfferBuilder().build(),
                Protos.TaskInfo.newBuilder()
                        .setTaskId(TestConstants.TASK_ID)
                        .setName(TestConstants.TASK_NAME)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .build(),
                Protos.ExecutorInfo.newBuilder().setExecutorId(
                        Protos.ExecutorID.newBuilder().setValue("executor")).build(),
                true);
        Assert.assertTrue(((LaunchOfferRecommendation)realRecommendation).shouldLaunch());

        Counter launchCounter = Metrics.getRegistry().counter("operation.launch_group");
        long val = launchCounter.getCount();

        Metrics.incrementRecommendations(Arrays.asList(realRecommendation, realRecommendation, realRecommendation));

        Assert.assertEquals(3, launchCounter.getCount() - val);
    }

    @Test
    public void testPlanGauge() {
        Metrics.PlanGauge gauge = new Metrics.PlanGauge();

        class GaugeTest {
            public Status status;
            public Integer expected;

            public GaugeTest(Status status, Integer expected) {
                this.status = status;
                this.expected = expected;
            }
        }

        GaugeTest[] tests = new GaugeTest[]{
                new GaugeTest(Status.ERROR, -1),
                new GaugeTest(Status.COMPLETE, 0),
                new GaugeTest(Status.WAITING, 1),
                new GaugeTest(Status.PENDING, 1),
                new GaugeTest(Status.PREPARED, 2),
                new GaugeTest(Status.IN_PROGRESS, 2),
                new GaugeTest(Status.STARTED, 2),
                new GaugeTest(Status.STARTING, 2)
        };

        for (GaugeTest test : tests) {
            gauge.setStatus(test.status);
            Assert.assertEquals("For status "+test.status+" expected status is "+test.expected,
                    test.expected,
                    gauge.getValue());
        }
    }

    @Test
    public void testPlanStatusNoNamespace() {
        String metricName = "plan_status.nonamespace";
        MetricRegistry registry = Metrics.getRegistry();
        Assert.assertEquals(0, registry.getGauges((name, metric) -> name.equals(metricName)).size());

        // Call set status. This will create the gauge.
        Metrics.setPlanStatus(Optional.empty(), "nonamespace", Status.ERROR);
        Assert.assertEquals(1, registry.getGauges((name, metric) -> name.equals(metricName)).size());
        Gauge gauge = registry.getGauges((name, metric) -> name.equals(metricName)).get(metricName);
        Assert.assertEquals(-1, gauge.getValue());

        // Verify that an update to status is applied to the same gauge.
        Metrics.setPlanStatus(Optional.empty(), "nonamespace", Status.IN_PROGRESS);
        Assert.assertEquals(2, gauge.getValue());
    }

    @Test
    public void testPlanStatusWithNamespace() {
        String metricName = "plan_status.namespace.namespaced";
        MetricRegistry registry = Metrics.getRegistry();
        Assert.assertEquals(0, registry.getGauges((name, metric) -> name.equals(metricName)).size());

        // Call set status. This will create the gauge.
        Metrics.setPlanStatus(Optional.of("namespace"), "namespaced", Status.ERROR);
        Assert.assertEquals(1, registry.getGauges((name, metric) -> name.equals(metricName)).size());
        Gauge gauge = registry.getGauges((name, metric) -> name.equals(metricName)).get(metricName);
        Assert.assertEquals(-1, gauge.getValue());

        // Verify that an update to status is applied to the same gauge.
        Metrics.setPlanStatus(Optional.of("namespace"), "namespaced", Status.IN_PROGRESS);
        Assert.assertEquals(2, gauge.getValue());
    }
}
