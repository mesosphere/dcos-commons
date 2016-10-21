package org.apache.mesos.scheduler;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.testing.CuratorTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.awaitility.Awaitility;
import org.junit.*;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * This class tests the DefaultScheduler class.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
public class DefaultSchedulerTest {
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @Rule public TestRule globalTimeout= new DisableOnDebug(new Timeout(10,TimeUnit.SECONDS));
    @Mock private SchedulerDriver mockSchedulerDriver;

    private static final String SERVICE_NAME = "test-service";
    private static final int TASK_A_COUNT = 1;
    private static final String TASK_A_NAME = "A";
    private static final double TASK_A_CPU = 1.0;
    private static final double TASK_A_MEM = 1000.0;
    private static final double TASK_A_DISK = 1500.0;
    private static final String TASK_A_CMD = "echo " + TASK_A_NAME;

    private static final int TASK_B_COUNT = 2;
    private static final String TASK_B_NAME = "B";
    private static final double TASK_B_CPU = 2.0;
    private static final double TASK_B_MEM = 2000.0;
    private static final double TASK_B_DISK = 2500.0;
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
            public List<TaskSet> getTaskSets() {
                return Arrays.asList(
                        TestTaskSetFactory.getTaskSet(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM,
                                TASK_A_DISK),
                        TestTaskSetFactory.getTaskSet(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU,
                                TASK_B_MEM,
                                TASK_B_DISK));
            }
        };

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(defaultScheduler);
    }

    @Test
    public void testEmptyOffers() {
        defaultScheduler.resourceOffers(mockSchedulerDriver, Collections.emptyList());
        verify(mockSchedulerDriver, times(1)).reconcileTasks(any());
        verify(mockSchedulerDriver, times(0)).acceptOffers(any(), anyCollection(), any());
        verify(mockSchedulerDriver, times(0)).declineOffer(any(), any());
    }

    @Test
    public void testLaunchA() throws InterruptedException {
        // Get first Block associated with Task A-0
        Plan plan = defaultScheduler.getPlan();
        Block blockTaskA0 = (Block) plan.getChildren().get(0).getChildren().get(0);
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
        Assert.assertEquals(5, operations.size());
        Assert.assertEquals(3, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH, operations));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(blockTaskA0).isInProgress(), equalTo(true));

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
        Phase phase0 = (Phase) plan.getChildren().get(0);
        Block blockTaskA0 = (Block) phase0.getChildren().get(0);
        Assert.assertTrue(blockTaskA0.isComplete());
        Assert.assertTrue(phase0.isComplete());

        // Get first Block of the second Phase associated with Task B-0
        Block blockTaskB0 = (Block) plan.getChildren().get(1).getChildren().get(0);
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
        Assert.assertEquals(5, operations.size());
        Assert.assertEquals(3, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH, operations));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(blockTaskB0).isInProgress(), equalTo(true));

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
        Block blockTaskA0 = (Block) plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(blockTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        UUID offerId = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getInsufficientOfferForTaskA(offerId)));
        defaultScheduler.awaitTermination();
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.PENDING, Status.PENDING, Status.PENDING)));
    }

    @Test
    public void updatePerTaskASpecification() throws InterruptedException, IOException {
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
            public List<TaskSet> getTaskSets() {
                return Arrays.asList(
                        TestTaskSetFactory.getTaskSet(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU * 2.0,
                                TASK_A_MEM * 2.0,
                                TASK_A_DISK),
                        TestTaskSetFactory.getTaskSet(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU,
                                TASK_B_MEM,
                                TASK_B_DISK));
            }
        };

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.PENDING, Status.COMPLETE, Status.PENDING)));
    }

    @Test
    public void updatePerTaskBSpecification() throws InterruptedException, IOException {
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
            public List<TaskSet> getTaskSets() {
                return Arrays.asList(
                        TestTaskSetFactory.getTaskSet(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM,
                                TASK_A_DISK),
                        TestTaskSetFactory.getTaskSet(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU * 2.0,
                                TASK_B_MEM * 2.0,
                                TASK_B_DISK));
            }
        };

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING)));
    }

    @Test
    public void updateTaskBCpuSpecification() throws InterruptedException, IOException {
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
            public List<TaskSet> getTaskSets() {
                return Arrays.asList(
                        TestTaskSetFactory.getTaskSet(
                                TASK_A_NAME,
                                TASK_A_COUNT,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM,
                                TASK_A_DISK),
                        TestTaskSetFactory.getTaskSet(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU * 2.0,
                                TASK_B_MEM,
                                TASK_B_DISK));
            }
        };

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING)));
    }

    @Test
    public void updateTaskTypeASpecification() throws InterruptedException, IOException {
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
            public List<TaskSet> getTaskSets() {
                return Arrays.asList(
                        TestTaskSetFactory.getTaskSet(
                                TASK_A_NAME,
                                TASK_A_COUNT + 1,
                                TASK_A_CMD,
                                TASK_A_CPU,
                                TASK_A_MEM,
                                TASK_A_DISK),
                        TestTaskSetFactory.getTaskSet(
                                TASK_B_NAME,
                                TASK_B_COUNT,
                                TASK_B_CMD,
                                TASK_B_CPU,
                                TASK_B_MEM,
                                TASK_B_DISK));
            }
        };

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertTrue(inExpectedState(
                plan,
                Arrays.asList(Status.COMPLETE, Status.PENDING, Status.COMPLETE, Status.PENDING)));
    }

    @Test
    public void testLaunchAndRecovery() throws Exception {
        // Get first Block associated with Task A-0
        Plan plan = defaultScheduler.getPlan();
        Block blockTaskA0 = (Block) plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(blockTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        UUID offerId1 = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getSufficientOfferForTaskA(offerId1)));
        ArgumentCaptor<Collection> operationsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
            (Collection<Protos.OfferID>) Matchers.argThat(contains(getOfferId(offerId1))),
            operationsCaptor.capture(),
            any());

        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();

        // Sent TASK_RUNNING status
        Protos.TaskID launchedTaskId = getTaskId(operations);
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, Protos.TaskState.TASK_RUNNING);
        defaultScheduler.statusUpdate(mockSchedulerDriver, runningStatus);

        // Wait for the Block to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(blockTaskA0).isComplete(), equalTo(true));
        Assert.assertTrue(inExpectedState(plan, Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING)));

        // Sent TASK_KILLED status
        runningStatus = getTaskStatus(launchedTaskId, Protos.TaskState.TASK_KILLED);
        defaultScheduler.statusUpdate(mockSchedulerDriver, runningStatus);

        reset(mockSchedulerDriver);

        // Make an offer sufficient to recover Task A-0 and launch Task B-0,
        // and also have some unused reserved resources for cleaning
        Protos.Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        cpus = ResourceUtils.setResourceId(cpus, UUID.randomUUID().toString());
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        mem = ResourceUtils.setResourceId(mem, UUID.randomUUID().toString());

        UUID offerId2 = UUID.randomUUID();
        Protos.Offer offer = Protos.Offer.newBuilder(getSufficientOfferForTaskB(offerId2))
                .addAllResources(operations.stream()
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();

        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer));
        defaultScheduler.awaitTermination();

        // Verify scheduler accepted resources twice, once for scheduling and once for recovery.
        verify(mockSchedulerDriver, times(2)).acceptOffers(any(), any(), any());
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
        defaultScheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, TestConstants.MASTER_INFO);
    }

    private Protos.Offer getInsufficientOfferForTaskA(UUID offerId) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpu(TASK_A_CPU / 2.0),
                                ResourceTestUtils.getUnreservedMem(TASK_A_MEM / 2.0)))
                .build();
    }

    private Protos.Offer getSufficientOfferForTaskA(UUID offerId) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpu(TASK_A_CPU),
                                ResourceTestUtils.getUnreservedMem(TASK_A_MEM),
                                ResourceTestUtils.getUnreservedDisk(TASK_A_DISK)))
                .build();
    }

    private Protos.Offer getSufficientOfferForTaskB(UUID offerId) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpu(TASK_B_CPU),
                                ResourceTestUtils.getUnreservedMem(TASK_B_MEM),
                                ResourceTestUtils.getUnreservedDisk(TASK_B_DISK)))
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
        for (Element phaseElement : plan.getChildren()) {
            Phase phase = (Phase) phaseElement;
            for (Element blockElement : phase.getChildren()) {
                Block block = (Block) blockElement;
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

        for (Element Element : plan.getChildren()) {
            i += Element.getChildren().size();
        }

        return i;
    }
}
