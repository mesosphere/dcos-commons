package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

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
        OfferRecommendation realRecommendation = getRecommendation(true);
        Assert.assertTrue(((LaunchOfferRecommendation)realRecommendation).shouldLaunch());

        OfferRecommendation suppressedRecommendation = getRecommendation(false);
        Assert.assertFalse(((LaunchOfferRecommendation)suppressedRecommendation).shouldLaunch());

        Counter launchCounter = Metrics.getRegistry().counter("operation.launch_group");

        long val = launchCounter.getCount();
        Metrics.OperationsCounter.getInstance().record(realRecommendation);
        Metrics.OperationsCounter.getInstance().record(realRecommendation);
        Metrics.OperationsCounter.getInstance().record(realRecommendation);
        Assert.assertEquals(3, launchCounter.getCount() - val);

        val = launchCounter.getCount();
        Metrics.OperationsCounter.getInstance().record(suppressedRecommendation);
        Metrics.OperationsCounter.getInstance().record(suppressedRecommendation);
        Assert.assertEquals(0, launchCounter.getCount() - val);
    }

    private static OfferRecommendation getRecommendation(boolean shouldLaunch) {
        return new LaunchOfferRecommendation(
                OfferTestUtils.getEmptyOfferBuilder().build(),
                Protos.TaskInfo.newBuilder()
                        .setTaskId(TestConstants.TASK_ID)
                        .setName(TestConstants.TASK_NAME)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .build(),
                Protos.ExecutorInfo.newBuilder().setExecutorId(
                        Protos.ExecutorID.newBuilder().setValue("executor")).build(),
                shouldLaunch,
                true);
    }
}
