package com.mesosphere.sdk.scheduler;

import com.codahale.metrics.Counter;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * This class tests the {@link Metrics} class.
 */
public class MetricsTest {
    @Before
    public void beforeEach() {
        Metrics.reset();
    }

    @Test
    public void emptyReceivedOffers() {
        Assert.assertEquals(0, Metrics.getReceivedOffers().getCount());
    }

    @Test
    public void incrementReceivedOffers() {
       Metrics.getReceivedOffers().inc();
       Assert.assertEquals(1, Metrics.getReceivedOffers().getCount());
    }

    @Test
    public void emptyProcessedOffers() {
        Assert.assertEquals(0, Metrics.getProcessedOffers().getCount());
    }

    @Test
    public void incrementProcessedOffers() {
        Metrics.getProcessedOffers().inc();
        Assert.assertEquals(1, Metrics.getProcessedOffers().getCount());
    }

    @Test
    public void emptyProcessOffers() {
        Assert.assertEquals(0, Metrics.getProcessOffersDuration().getCount());
    }

    @Test
    public void timeProcessOffers() {
        Metrics.getProcessOffersDuration().time().stop();
        Assert.assertEquals(1, Metrics.getProcessOffersDuration().getCount());
    }

    @Test
    public void emptyRevives() {
        Assert.assertEquals(0, Metrics.getRevives().getCount());
    }

    @Test
    public void incrementRevives() {
        Metrics.getRevives().inc();
        Assert.assertEquals(1, Metrics.getRevives().getCount());
    }

    @Test
    public void emptyReviveThrottles() {
        Assert.assertEquals(0, Metrics.getReviveThrottles().getCount());
    }

    @Test
    public void incrementReviveThrottles() {
        Metrics.getReviveThrottles().inc();
        Assert.assertEquals(1, Metrics.getReviveThrottles().getCount());
    }

    @Test
    public void emptyDeclinesShort() {
        Assert.assertEquals(0, Metrics.getDeclinesShort().getCount());
    }

    @Test
    public void incrementDeclinesShort() {
        Metrics.getDeclinesShort().inc();
        Assert.assertEquals(1, Metrics.getDeclinesShort().getCount());
    }

    @Test
    public void emptyDeclinesLong() {
        Assert.assertEquals(0, Metrics.getDeclinesLong().getCount());
    }

    @Test
    public void incrementDeclinesLong() {
        Metrics.getDeclinesLong().inc();
        Assert.assertEquals(1, Metrics.getDeclinesLong().getCount());
    }

    @Test
    public void taskRunning() {
        Protos.TaskStatus taskStatus = Protos.TaskStatus.newBuilder()
                .setState(Protos.TaskState.TASK_RUNNING)
                .setTaskId(TestConstants.TASK_ID)
                .build();

        Assert.assertEquals(0, Metrics.getRegistry().getCounters().size());
        Metrics.record(taskStatus);
        Assert.assertEquals(1, Metrics.getRegistry().getCounters().size());

        String metricName = Metrics.getRegistry().getCounters().firstKey();
        Counter counter = Metrics.getRegistry().counter(metricName);
        Assert.assertEquals(1, counter.getCount());
    }

    @Test
    public void realTaskLaunch() throws Exception {
        OfferRecommendation recommendation = getRealRecommendation();

        Assert.assertEquals(0, Metrics.getRegistry().getCounters().size());
        Metrics.OperationsCounter.getInstance().record(recommendation);
        Assert.assertEquals(1, Metrics.getRegistry().getCounters().size());

        String metricName = Metrics.getRegistry().getCounters().firstKey();
        Counter counter = Metrics.getRegistry().counter(metricName);
        Assert.assertEquals(1, counter.getCount());
    }

    @Test
    public void suppressedTaskLaunch() throws Exception {
        OfferRecommendation recommendation = getSuppressedRecommendation();

        Assert.assertEquals(0, Metrics.getRegistry().getCounters().size());
        Metrics.OperationsCounter.getInstance().record(recommendation);
        Assert.assertEquals(0, Metrics.getRegistry().getCounters().size());
    }

    private OfferRecommendation getRealRecommendation() {
        return getRecommendation(true);
    }

    private OfferRecommendation getSuppressedRecommendation() {
        return getRecommendation(false);
    }

    private OfferRecommendation getRecommendation(boolean shouldLaunch) {
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
