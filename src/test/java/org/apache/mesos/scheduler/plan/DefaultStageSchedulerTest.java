package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.Arrays;

import static org.powermock.api.mockito.PowerMockito.when;

/**
 * This class tests the DefaultStageScheduler.
 */
public class DefaultStageSchedulerTest {
    @Mock
    OfferAccepter offerAccepter;

    @Mock
    SchedulerDriver driver;

    @Mock
    Block block;

    private DefaultStageScheduler scheduler;

    @Before
    public void beforeEach() throws Exception{
        MockitoAnnotations.initMocks(this);
        when(block.isPending()).thenReturn(true);
        when(block.start()).thenReturn(getTestOfferRequirement());
        scheduler = new DefaultStageScheduler(offerAccepter);
    }

    @Test
    public void testNullArguments() {
        Assert.assertEquals(0, scheduler.resourceOffers(driver, null, block).size());
        Assert.assertEquals(0, scheduler.resourceOffers(null, new ArrayList<Protos.Offer>(), block).size());
        Assert.assertEquals(0, scheduler.resourceOffers(driver, new ArrayList<Protos.Offer>(), null).size());
    }

    @Test
    public void testPendingBlock() {
        Assert.assertEquals(0, scheduler.resourceOffers(driver, new ArrayList<Protos.Offer>(), block).size());
    }

    OfferRequirement getTestOfferRequirement() throws Exception {
        Protos.Resource desiredTaskCpu = ResourceUtils.getDesiredScalar(
                ResourceTestUtils.testRole,
                ResourceTestUtils.testPrincipal,
                "cpus",
                1.0);

        return new OfferRequirement(
                Arrays.asList(TaskTestUtils.getTaskInfo(desiredTaskCpu)));
    }
}
