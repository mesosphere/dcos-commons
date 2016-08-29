package org.apache.mesos.scheduler;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.ResourceTestUtils;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.Status;
import org.apache.mesos.specification.*;
import org.apache.mesos.testing.CuratorTestUtils;
import org.awaitility.Awaitility;
import org.junit.*;
import org.junit.rules.Timeout;
import org.mockito.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Created by gabriel on 8/28/16.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
public class DefaultSchedulerTest {
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @Rule public Timeout globalTimeout= new Timeout(1, TimeUnit.SECONDS);
    @Mock private SchedulerDriver mockSchedulerDriver;

    private static final String SERVICE_NAME = "test-service";
    private static final int TASK_A_COUNT = 1;
    private static final String TASK_A_NAME = "A";
    private static final double TASK_A_CPU = 1.0;
    private static final double TASK_A_MEM = 1000.0;
    private static final String TASK_A_CMD = "echo " + TASK_A_NAME;

    private static final int TASK_B_COUNT = 2;
    private static final String TASK_B_NAME = "B";
    private static final double TASK_B_CPU = 2.0;
    private static final double TASK_B_MEM = 2000.0;
    private static final String TASK_B_CMD = "echo " + TASK_B_NAME;

    private static TestingServer testingServer;
    private ServiceSpecification serviceSpecification;
    private DefaultScheduler defaultScheduler;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);
        serviceSpecification = new ServiceSpecification() {
            @Override
            public String getName() {
                return SERVICE_NAME;
            }

            @Override
            public List<TaskTypeSpecification> getTaskSpecifications() {
                return Arrays.asList(
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM),
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU,
                                TASK_B_MEM));
            }
        };
        defaultScheduler = new DefaultScheduler(serviceSpecification, testingServer.getConnectString());
        register();
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(defaultScheduler);
    }

    @Test
    public void testEmptyOffers() {
        defaultScheduler.resourceOffers(mockSchedulerDriver, Collections.emptyList());
        Mockito.verifyZeroInteractions(mockSchedulerDriver);
    }

    @Test
    public void testLaunchA() throws InterruptedException {
        // Get first Block associated with Task A-0
        Plan plan = defaultScheduler.getPlan();
        Block blockTaskA0 = plan.getPhases().get(0).getBlock(0);
        Assert.assertTrue(blockTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        UUID offerId = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getSufficientOfferForTaskA(offerId)));
        ArgumentCaptor<Collection> operationsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                (Collection<Protos.OfferID>) Matchers.argThat(contains(getOfferId(offerId))),
                operationsCaptor.capture(),
                any());

        // Verify 2 Reserve and 1 Launch Operations were executed
        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Assert.assertEquals(3, operations.size());
        Assert.assertEquals(2, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH, operations));
        Assert.assertTrue(blockTaskA0.isInProgress());

        // Sent TASK_RUNNING status
        Protos.TaskID launchedTaskId = getTaskId(operations);
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, Protos.TaskState.TASK_RUNNING);
        defaultScheduler.statusUpdate(mockSchedulerDriver, runningStatus);

        // Wait for the Block to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(blockTaskA0).isComplete(), equalTo(true));
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING)));
    }

    @Test
    public void testLaunchB() throws InterruptedException {
        Plan plan = defaultScheduler.getPlan();

        // Launch A-0
        testLaunchA();
        Block blockTaskA0 = plan.getPhases().get(0).getBlock(0);
        Assert.assertTrue(blockTaskA0.isComplete());

        // Get first Block of the second Phase associated with Task B-0
        Block blockTaskB0 = plan.getPhases().get(1).getBlock(0);
        Assert.assertTrue(blockTaskB0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        UUID offerId = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getSufficientOfferForTaskB(offerId)));
        ArgumentCaptor<Collection> operationsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                (Collection<Protos.OfferID>) Matchers.argThat(contains(getOfferId(offerId))),
                operationsCaptor.capture(),
                any());

        // Verify 2 Reserve and 1 Launch Operations were executed
        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Assert.assertEquals(3, operations.size());
        Assert.assertEquals(2, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH, operations));
        Assert.assertTrue(blockTaskB0.isInProgress());

        // Sent TASK_RUNNING status
        Protos.TaskID launchedTaskId = getTaskId(operations);
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, Protos.TaskState.TASK_RUNNING);
        defaultScheduler.statusUpdate(mockSchedulerDriver, runningStatus);

        // Wait for the Block to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(blockTaskB0).isComplete(), equalTo(true));
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.PENDING)));
    }

    @Test
    public void testFailLaunchA() throws InterruptedException {
        // Get first Block associated with Task A-0
        Plan plan = defaultScheduler.getPlan();
        Block blockTaskA0 = plan.getPhases().get(0).getBlock(0);
        Assert.assertTrue(blockTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        UUID offerId = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getInsufficientOfferForTaskA(offerId)));
        defaultScheduler.awaitTermination();
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.PENDING, Status.PENDING, Status.PENDING)));
    }

    @Test
    public void updatePerTaskASpecification() throws InterruptedException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        // Double TaskA cpu and mem requirements
        serviceSpecification = new ServiceSpecification() {
            @Override
            public String getName() {
                return SERVICE_NAME;
            }

            @Override
            public List<TaskTypeSpecification> getTaskSpecifications() {
                return Arrays.asList(
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU * 2.0,
                                TASK_A_MEM * 2.0),
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU,
                                TASK_B_MEM));
            }
        };
        defaultScheduler = new DefaultScheduler(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.PENDING, Status.COMPLETE, Status.PENDING)));
    }

    @Test
    public void updatePerTaskBSpecification() throws InterruptedException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        // Double TaskB cpu and mem requirements
        serviceSpecification = new ServiceSpecification() {
            @Override
            public String getName() {
                return SERVICE_NAME;
            }

            @Override
            public List<TaskTypeSpecification> getTaskSpecifications() {
                return Arrays.asList(
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM),
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU * 2.0,
                                TASK_B_MEM * 2.0));
            }
        };
        defaultScheduler = new DefaultScheduler(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING)));
    }

    @Test
    public void updateTaskTypeASpecification() throws InterruptedException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        // Increase count of TaskA tasks.
        serviceSpecification = new ServiceSpecification() {
            @Override
            public String getName() {
                return SERVICE_NAME;
            }

            @Override
            public List<TaskTypeSpecification> getTaskSpecifications() {
                return Arrays.asList(
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_A_NAME,
                                TASK_A_COUNT + 1,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM),
                        TestTaskSpecificationFactory.getTaskSpecification(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU,
                                TASK_B_MEM));
            }
        };
        defaultScheduler = new DefaultScheduler(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertTrue(inExpectedState(
                plan,
                Arrays.asList(Status.COMPLETE, Status.PENDING, Status.COMPLETE, Status.PENDING)));
    }


    private int countOperationType(
            Protos.Offer.Operation.Type operationType,
            Collection<Protos.Offer.Operation> operations) {
        int count = 0;
        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(operationType)) {
                count++;
            }
        }
        return count;
    }

    private Protos.TaskID getTaskId(Collection<Protos.Offer.Operation> operations) {
        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                return operation.getLaunch().getTaskInfosList().get(0).getTaskId();
            }
        }

        return null;
    }

    private Protos.TaskStatus getTaskStatus(Protos.TaskID taskID, Protos.TaskState state) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(state)
                .build();
    }

    private void register() {
        defaultScheduler.registered(mockSchedulerDriver, ResourceTestUtils.testFrameworkId, ResourceTestUtils.testMasterInfo);
    }

    private Protos.Offer getInsufficientOfferForTaskA(UUID offerId) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(ResourceTestUtils.testFrameworkId)
                .setSlaveId(ResourceTestUtils.testSlaveId)
                .setHostname(ResourceTestUtils.testHostname)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getOfferedUnreservedScalar("cpus", TASK_A_CPU / 2.0),
                                ResourceTestUtils.getOfferedUnreservedScalar("mem", TASK_A_MEM / 2.0)))
                .build();
    }

    private Protos.Offer getSufficientOfferForTaskA(UUID offerId) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(ResourceTestUtils.testFrameworkId)
                .setSlaveId(ResourceTestUtils.testSlaveId)
                .setHostname(ResourceTestUtils.testHostname)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getOfferedUnreservedScalar("cpus", TASK_A_CPU),
                                ResourceTestUtils.getOfferedUnreservedScalar("mem", TASK_A_MEM)))
                .build();
    }

    private Protos.Offer getSufficientOfferForTaskB(UUID offerId) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(ResourceTestUtils.testFrameworkId)
                .setSlaveId(ResourceTestUtils.testSlaveId)
                .setHostname(ResourceTestUtils.testHostname)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getOfferedUnreservedScalar("cpus", TASK_B_CPU),
                                ResourceTestUtils.getOfferedUnreservedScalar("mem", TASK_B_MEM)))
                .build();
    }

    private Protos.OfferID getOfferId(UUID id) {
        return Protos.OfferID.newBuilder().setValue(id.toString()).build();
    }

    private boolean inExpectedState(Plan plan, List<Status> statuses) {
        if (countBlocks(plan) != statuses.size()) {
            return false;
        }

        int i = 0;
        for (Phase phase : plan.getPhases()) {
            for (Block block : phase.getBlocks()) {
                switch (statuses.get(i)) {
                    case PENDING:
                        if (!block.isPending()) {
                            return false;
                        }
                        break;
                    case IN_PROGRESS:
                        if (!block.isInProgress()) {
                            return false;
                        }
                        break;
                    case COMPLETE:
                        if (!block.isComplete()) {
                            return false;
                        }
                        break;
                    default:
                        return false;
                }

                i++;
            }
        }

        return true;
    }

    private int countBlocks(Plan plan) {
        int i = 0;
        for (Phase phase : plan.getPhases()) {
            for (Block block : phase.getBlocks()) {
                i++;
            }
        }

        return i;
    }
}
