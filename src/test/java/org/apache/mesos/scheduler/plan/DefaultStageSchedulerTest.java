package org.apache.mesos.scheduler.plan;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.ArrayList;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DefaultStageScheduler}.
 */
public class DefaultStageSchedulerTest {

    @Mock private OfferAccepter mockOfferAccepter;
    @Mock private SchedulerDriver mockSchedulerDriver;

    private DefaultStageScheduler scheduler;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        scheduler = new DefaultStageScheduler(mockOfferAccepter);
    }

    @Test
    public void testNullBlock() {
        assertTrue(scheduler.resourceOffers(
                mockSchedulerDriver, new ArrayList<>(), null).isEmpty());
        verifyZeroInteractions(mockOfferAccepter, mockSchedulerDriver);
    }

    @Test
    public void testComplete() {
        assertTrue("TODO", false);
    }
}
