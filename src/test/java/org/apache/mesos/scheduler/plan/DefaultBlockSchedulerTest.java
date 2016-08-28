package org.apache.mesos.scheduler.plan;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.*;

import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.scheduler.TaskKiller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DefaultBlockScheduler}.
 */
public class DefaultBlockSchedulerTest {

    private static final List<TaskInfo> TASKINFOS = Arrays.asList(TaskInfo.newBuilder()
            .setName("hi")
            .setTaskId(TaskUtils.toTaskId("hi"))
            .setSlaveId(SlaveID.newBuilder().setValue("slaveid").build())
            .build());
    private static final List<Offer> OFFERS = Arrays.asList(Offer.newBuilder()
            .setId(OfferID.newBuilder().setValue("offerid").build())
            .setFrameworkId(FrameworkID.newBuilder().setValue("frameworkid").build())
            .setSlaveId(SlaveID.newBuilder().setValue("slaveid").build())
            .setHostname("hello")
            .build());
    private static final List<OfferRecommendation> RECOMMENDATIONS =
            Arrays.asList(new OfferRecommendation() {
                @Override public Operation getOperation() { return null; }
                @Override public Offer getOffer() { return null; }
            });
    private static final List<OfferID> ACCEPTED_IDS =
            Arrays.asList(OfferID.newBuilder().setValue("offer").build());

    @Mock private OfferAccepter mockOfferAccepter;
    @Mock private OfferEvaluator mockOfferEvaluator;
    @Mock private SchedulerDriver mockSchedulerDriver;
    @Mock private TaskKiller mockTaskKiller;

    private DefaultBlockScheduler scheduler;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        scheduler = new DefaultBlockScheduler(mockOfferAccepter, mockOfferEvaluator, mockTaskKiller);
    }

    @Test
    public void testNullParams() {
        assertTrue(scheduler.resourceOffers(null, OFFERS, new TestBlock()).isEmpty());
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, null, new TestBlock()).isEmpty());
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, null).isEmpty());
        verifyZeroInteractions(mockOfferAccepter, mockSchedulerDriver);
    }

    @Test
    public void testNonPendingBlock() {
        TestBlock block = new TestBlock().setStatus(Status.IN_PROGRESS);
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, block).isEmpty());
        assertTrue(block.isInProgress());
    }

    @Test
    public void testStartNoRequirement() {
        TestBlock block = new TestOfferBlock(null).setStatus(Status.PENDING);
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, block).isEmpty());
        assertTrue(block.isPending());
    }

    @Test
    public void testEvaluateNoRecommendations() throws InvalidRequirementException {
        OfferRequirement requirement = new OfferRequirement(TASKINFOS);
        TestOfferBlock block =(TestOfferBlock)new TestOfferBlock(requirement)
                .setStatus(Status.PENDING);
        when(mockOfferEvaluator.evaluate(requirement, OFFERS)).thenReturn(new ArrayList<>());

        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, block).isEmpty());

        assertTrue(block.operationsOptional.isPresent());
        verify(mockOfferEvaluator).evaluate(requirement, OFFERS);
        assertTrue(block.isPending());
    }

    @Test
    public void testEvaluateNoAcceptedOffers() throws InvalidRequirementException {
        OfferRequirement requirement = new OfferRequirement(TASKINFOS);
        TestOfferBlock block =(TestOfferBlock)new TestOfferBlock(requirement)
                .setStatus(Status.PENDING);
        when(mockOfferEvaluator.evaluate(requirement, OFFERS)).thenReturn(RECOMMENDATIONS);
        when(mockOfferAccepter.accept(mockSchedulerDriver, RECOMMENDATIONS))
                .thenReturn(new ArrayList<>());

        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, block).isEmpty());

        assertTrue(block.operationsOptional.isPresent());
        verify(mockOfferAccepter).accept(mockSchedulerDriver, RECOMMENDATIONS);
        assertTrue(block.isPending());
    }

    @Test
    public void testEvaluateAcceptedOffers() throws InvalidRequirementException {
        OfferRequirement requirement = new OfferRequirement(TASKINFOS);
        TestOfferBlock block =(TestOfferBlock)new TestOfferBlock(requirement)
                .setStatus(Status.PENDING);
        when(mockOfferEvaluator.evaluate(requirement, OFFERS)).thenReturn(RECOMMENDATIONS);
        when(mockOfferAccepter.accept(mockSchedulerDriver, RECOMMENDATIONS))
                .thenReturn(ACCEPTED_IDS);

        assertEquals(ACCEPTED_IDS, scheduler.resourceOffers(mockSchedulerDriver, OFFERS, block));

        assertTrue(block.operationsOptional.isPresent());
        assertTrue(block.isInProgress());
    }

    private static class TestOfferBlock extends TestBlock {
        private final OfferRequirement requirement;
        private Optional<Collection<Operation>> operationsOptional;

        private TestOfferBlock(OfferRequirement requirementToReturn) {
            super();
            this.requirement = requirementToReturn;
        }

        @Override
        public Optional<OfferRequirement> start() {
            super.start();
            return Optional.of(requirement);
        }

        @Override
        public void updateOfferStatus(Optional<Collection<Operation>> operationsOptional) {
            super.updateOfferStatus(operationsOptional);
            this.operationsOptional = operationsOptional;
        }
    }
}
