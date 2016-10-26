package org.apache.mesos.scheduler;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.scheduler.plan.Status;
import org.apache.mesos.specification.DefaultServiceSpecification;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testing.CuratorTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.awaitility.Awaitility;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.*;

/**
 * This class tests the DefaultScheduler class.
 */
@SuppressWarnings({"PMD.TooManyStaticImports", "unchecked"})
public class DefaultSchedulerTest {
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @Rule public TestRule globalTimeout = new DisableOnDebug(new Timeout(10, TimeUnit.SECONDS));
    @Mock private SchedulerDriver mockSchedulerDriver;
    @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;
    @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor2;

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
    private DefaultScheduler defaultScheduler;
    private StateStore stateStore;
    private EnvironmentVariables environmentVariables;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");
        ServiceSpecification serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
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
                                TASK_B_DISK)));

        stateStore = new CuratorStateStore(serviceSpecification.getName(), testingServer.getConnectString());
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
        verify(mockSchedulerDriver, times(0)).acceptOffers(any(), anyCollectionOf(Protos.Offer.Operation.class), any());
        verify(mockSchedulerDriver, times(0)).declineOffer(any(), any());
    }

    @Test
    public void testLaunchA() throws InterruptedException {
        installBlock(0, 0, getSufficientOfferForTaskA());

        Plan plan = defaultScheduler.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getBlockStatuses(plan));
    }

    @Test
    public void testLaunchB() throws InterruptedException {
        // Launch A-0
        testLaunchA();
        installBlock(1, 0, getSufficientOfferForTaskB());

        Plan plan = defaultScheduler.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.PENDING), getBlockStatuses(plan));
    }

    @Test
    public void testFailLaunchA() throws InterruptedException {
        // Get first Block associated with Task A-0
        Plan plan = defaultScheduler.getPlan();
        Block blockTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(blockTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        UUID offerId = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getInsufficientOfferForTaskA(offerId)));
        defaultScheduler.awaitTermination();
        Assert.assertEquals(Arrays.asList(Status.PENDING, Status.PENDING, Status.PENDING), getBlockStatuses(plan));
    }

    @Test
    public void updatePerTaskASpecification() throws InterruptedException, IOException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        // Double TaskA cpu and mem requirements
        ServiceSpecification serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
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
                                TASK_B_DISK)));

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertEquals(Arrays.asList(Status.PENDING, Status.COMPLETE, Status.PENDING), getBlockStatuses(plan));
    }

    @Test
    public void updatePerTaskBSpecification() throws InterruptedException, IOException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        // Double TaskB cpu and mem requirements
        ServiceSpecification serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
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
                                TASK_B_DISK)));

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getBlockStatuses(plan));
    }

    @Test
    public void updateTaskBCpuSpecification() throws InterruptedException, IOException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        // Double TaskB cpu requirements
        ServiceSpecification serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
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
                                TASK_B_DISK)));

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getBlockStatuses(plan));
    }

    @Test
    public void updateTaskTypeASpecification() throws InterruptedException, IOException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        // Increase count of TaskA tasks.
        ServiceSpecification serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
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
                                TASK_B_DISK)));

        defaultScheduler = DefaultScheduler.create(serviceSpecification, testingServer.getConnectString());
        register();

        Plan plan = defaultScheduler.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.COMPLETE, Status.PENDING), getBlockStatuses(plan));
    }

    @Test
    public void testLaunchAndRecovery() throws Exception {
        // Get first Block associated with Task A-0
        Plan plan = defaultScheduler.getPlan();
        Block blockTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(blockTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        Protos.Offer offer1 = getSufficientOfferForTaskA();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer1));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer1.getId())),
                operationsCaptor.capture(),
                any());

        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Protos.TaskID launchedTaskId = getTaskId(operations);

        // Sent TASK_RUNNING status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Block to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(blockTaskA0).isComplete(), equalTo(true));
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getBlockStatuses(plan));

        // Sent TASK_KILLED status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_KILLED);

        reset(mockSchedulerDriver);

        // Make offers sufficient to recover Task A-0 and launch Task B-0,
        // and also have some unused reserved resources for cleaning, and verify that only one of those three happens.
        Protos.Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        cpus = ResourceUtils.setResourceId(cpus, UUID.randomUUID().toString());
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        mem = ResourceUtils.setResourceId(mem, UUID.randomUUID().toString());

        Protos.Offer offerA = Protos.Offer.newBuilder(getSufficientOfferForTaskA())
                .addAllResources(operations.stream()
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();
        Protos.Offer offerB = Protos.Offer.newBuilder(getSufficientOfferForTaskB())
                .addAllResources(operations.stream()
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();
        Protos.Offer offerC = Protos.Offer.newBuilder(getSufficientOfferForTaskB())
                .addAllResources(operations.stream()
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();

        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offerA, offerB, offerC));
        defaultScheduler.awaitTermination();

        // Verify that acceptOffer is called thrice, once each for recovery, launch, and cleanup.
        // Use a separate captor as the other one was already used against an acceptOffers call in this test case.
        verify(mockSchedulerDriver, times(3)).acceptOffers(
                any(),
                operationsCaptor2.capture(),
                any());
        final List<Collection<Protos.Offer.Operation>> allOperations = operationsCaptor2.getAllValues();
        Assert.assertEquals(3, allOperations.size());
        boolean recovery = false;
        boolean launch = false;
        boolean unreserve = false;

        for (Collection<Protos.Offer.Operation> operationSet : allOperations) {
            switch (operationSet.size()) {
                case 1:
                    // One LAUNCH operation
                    if (((Protos.Offer.Operation) operationSet.iterator().next()).getType()
                            == Protos.Offer.Operation.Type.LAUNCH) {
                        recovery = true;
                    }
                    break;
                case 2:
                    // Two UNRESERVE operations
                    if (operationSet.stream().allMatch(object -> ((Protos.Offer.Operation) object).getType()
                            == Protos.Offer.Operation.Type.UNRESERVE)) {
                        recovery = true;
                    }
                    unreserve = true;
                    break;
                case 5:
                    // Three RESERVE, One CREATE and One LAUNCH operation
                    int reserveOp = 0;
                    int createOp = 0;
                    int launchOp = 0;
                    for (Protos.Offer.Operation operation : operationSet) {
                        switch (operation.getType()) {
                            case RESERVE:
                                ++reserveOp;
                                break;
                            case CREATE:
                                ++createOp;
                                break;
                            case LAUNCH:
                                ++launchOp;
                                break;
                            default:
                                Assert.assertTrue("Expected RESERVE, CREATE, or LAUNCH, got " + operation.getType(), false);
                        }
                    }
                    if (reserveOp == 3 && createOp == 1 && launchOp == 1) {
                        launch = true;
                    }
                    break;
                default:
                    break;
            }
        }

        Assert.assertTrue(recovery);
        Assert.assertTrue(launch);
        Assert.assertTrue(unreserve);
    }

    @Test
    public void testSuppress() {
        install();
    }

    @Test
    public void testRevive() {
        List<Protos.TaskID> taskIds = install();
        statusUpdate(taskIds.get(0), Protos.TaskState.TASK_FAILED);

        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(stateStore).isSuppressed(), equalTo(false));
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

    private Protos.Offer getSufficientOfferForTaskA() {
        UUID offerId = UUID.randomUUID();

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

    private Protos.Offer getSufficientOfferForTaskB() {
        UUID offerId = UUID.randomUUID();

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

    private static List<Status> getBlockStatuses(Plan plan) {
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .map(block -> block.getStatus())
                .collect(Collectors.toList());
    }

    private static <T> Collection<T> collectionThat(final Matcher<Iterable<? extends T>> matcher) {
        return Matchers.argThat(new BaseMatcher<Collection<T>>() {
            @Override
            public boolean matches(Object item) {
                return matcher.matches(item);
            }

            @Override
            public void describeTo(Description description) {
                matcher.describeTo(description);
            }
        });
    }

    private Protos.TaskID installBlock(int phaseIndex, int blockIndex, Protos.Offer offer) {
        // Get first Block associated with Task A-0
        Plan plan = defaultScheduler.getPlan();
        List<Protos.Offer> offers = Arrays.asList(offer);
        Protos.OfferID offerId = offer.getId();
        Block block = plan.getChildren().get(phaseIndex).getChildren().get(blockIndex);
        Assert.assertTrue(block.isPending());

        // Offer sufficient Resource and wait for its acceptance

        defaultScheduler.resourceOffers(mockSchedulerDriver, offers);
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                (Collection<Protos.OfferID>) Matchers.argThat(contains(offerId)),
                operationsCaptor.capture(),
                any());

        // Verify 2 Reserve and 1 Launch Operations were executed
        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Assert.assertEquals(5, operations.size());
        Assert.assertEquals(3, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH, operations));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(block).isInProgress(), equalTo(true));

        // Sent TASK_RUNNING status
        Protos.TaskID taskId = getTaskId(operations);
        statusUpdate(getTaskId(operations), Protos.TaskState.TASK_RUNNING);

        // Wait for the Block to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(block).isComplete(), equalTo(true));

        return taskId;
    }

    private void statusUpdate(Protos.TaskID launchedTaskId, Protos.TaskState state) {
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, state);
        defaultScheduler.statusUpdate(mockSchedulerDriver, runningStatus);
    }

    /** Installs the service. */
    private List<Protos.TaskID> install() {
        List<Protos.TaskID> taskIds = new ArrayList<>();

        Plan plan = defaultScheduler.getPlan();
        taskIds.add(installBlock(0, 0, getSufficientOfferForTaskA()));
        taskIds.add(installBlock(1, 0, getSufficientOfferForTaskB()));
        taskIds.add(installBlock(1, 1, getSufficientOfferForTaskB()));

        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE), getBlockStatuses(plan));
        Assert.assertTrue(stateStore.isSuppressed());

        return taskIds;
    }
}
