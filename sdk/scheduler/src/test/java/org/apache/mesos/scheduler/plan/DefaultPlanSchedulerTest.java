package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.*;
import org.apache.mesos.scheduler.TaskKiller;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultPlanScheduler}.
 */
public class DefaultPlanSchedulerTest {

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

    private DefaultPlanScheduler scheduler;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        scheduler = new DefaultPlanScheduler(mockOfferAccepter, mockOfferEvaluator, mockTaskKiller);
    }

    @Test
    public void testNullParams() {
        assertTrue(scheduler.resourceOffers(null, OFFERS, Arrays.asList(new TestStep())).isEmpty());
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, null, Arrays.asList(new TestStep())).isEmpty());
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, null).isEmpty());
        verifyZeroInteractions(mockOfferAccepter, mockSchedulerDriver);
    }

    @Test
    public void testNonPendingStep() {
        TestStep step = new TestStep();
        step.setStatus(Status.IN_PROGRESS);
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, Arrays.asList(step)).isEmpty());
        assertTrue(step.isInProgress());
    }

    @Test
    public void testStartNoRequirement() {
        TestStep step = new TestStep();
        step.setStatus(Status.PENDING);
        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, Arrays.asList(step)).isEmpty());
        assertTrue(step.isPending());
    }

    @Test
    public void testEvaluateNoRecommendations() throws InvalidRequirementException {
        OfferRequirement requirement = OfferRequirement.create(TestConstants.TASK_TYPE, 0, TASKINFOS);
        TestOfferStep step = new TestOfferStep(requirement);
        step.setStatus(Status.PENDING);
        when(mockOfferEvaluator.evaluate(requirement, OFFERS)).thenReturn(new ArrayList<>());

        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, Arrays.asList(step)).isEmpty());
        assertTrue(step.operations.isEmpty());
        verify(mockOfferEvaluator).evaluate(requirement, OFFERS);
        assertTrue(step.isPending());
    }

    @Test
    public void testEvaluateNoAcceptedOffers() throws InvalidRequirementException {
        OfferRequirement requirement = OfferRequirement.create(TestConstants.TASK_TYPE, 0, TASKINFOS);
        TestOfferStep step = new TestOfferStep(requirement);
        step.setStatus(Status.PENDING);
        when(mockOfferEvaluator.evaluate(requirement, OFFERS)).thenReturn(RECOMMENDATIONS);
        when(mockOfferAccepter.accept(mockSchedulerDriver, RECOMMENDATIONS)).thenReturn(new ArrayList<>());

        assertTrue(scheduler.resourceOffers(mockSchedulerDriver, OFFERS, Arrays.asList(step)).isEmpty());
        assertTrue(step.operations.isEmpty());
        verify(mockOfferAccepter).accept(mockSchedulerDriver, RECOMMENDATIONS);
        assertTrue(step.isPending());
    }

    @Test
    public void testEvaluateAcceptedOffers() throws InvalidRequirementException {
        OfferRequirement requirement = OfferRequirement.create(TestConstants.TASK_TYPE, 0, TASKINFOS);
        TestOfferStep step = new TestOfferStep(requirement);
        step.setStatus(Status.PENDING);
        when(mockOfferEvaluator.evaluate(requirement, OFFERS)).thenReturn(RECOMMENDATIONS);
        when(mockOfferAccepter.accept(mockSchedulerDriver, RECOMMENDATIONS)).thenReturn(ACCEPTED_IDS);

        assertEquals(ACCEPTED_IDS, scheduler.resourceOffers(mockSchedulerDriver, OFFERS, Arrays.asList(step)));
        assertFalse(step.operations.isEmpty());
        assertTrue(step.isInProgress());
    }

    private static class TestOfferStep extends TestStep {
        private final OfferRequirement requirement;
        private Collection<Operation> operations;

        private TestOfferStep(OfferRequirement requirementToReturn) {
            super();
            this.requirement = requirementToReturn;
            this.operations = Collections.emptyList();
        }

        @Override
        public Optional<OfferRequirement> start() {
            super.start();
            if (requirement == null) {
                return Optional.empty();
            } else {
                return Optional.of(requirement);
            }
        }

        @Override
        public void updateOfferStatus(Collection<Operation> operations) {
            super.updateOfferStatus(operations);
            this.operations = operations;
        }
    }
}
