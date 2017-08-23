package com.mesosphere.sdk.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.TestPlacementUtils;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.testutils.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.awaitility.Awaitility;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.mockito.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mesosphere.sdk.dcos.DcosConstants.DEFAULT_GPU_POLICY;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.*;

/**
 * This class tests the DefaultScheduler class.
 */
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.AvoidUsingHardCodedIP"})
public class DefaultSchedulerTest {
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @Rule
    public TestRule globalTimeout = new DisableOnDebug(new Timeout(30, TimeUnit.SECONDS));
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    @Mock
    private SchedulerDriver mockSchedulerDriver;
    @Mock
    private SchedulerFlags mockSchedulerFlags;
    @Captor
    private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;
    @Captor
    private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor2;
    public static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

    private static final String TASK_IP = "9.9.9.9";

    private static final String SERVICE_NAME = "test-service";
    private static final int TASK_A_COUNT = 1;
    private static final String TASK_A_POD_NAME = "POD-A";
    private static final String TASK_A_NAME = "A";
    private static final double TASK_A_CPU = 1.0;
    private static final double UPDATED_TASK_A_CPU = TASK_A_CPU + 1.0;
    private static final double TASK_A_MEM = 1000.0;
    private static final double TASK_A_DISK = 1500.0;
    private static final String TASK_A_CMD = "echo " + TASK_A_NAME;

    private static final int TASK_B_COUNT = 2;
    private static final String TASK_B_POD_NAME = "POD-B";
    private static final String TASK_B_NAME = "B";
    private static final double TASK_B_CPU = 2.0;
    private static final double TASK_B_MEM = 2000.0;
    private static final double UPDATED_TASK_B_MEM = 2000.0 * 2;
    private static final double TASK_B_DISK = 2500.0;
    private static final String TASK_B_CMD = "echo " + TASK_B_NAME;

    private static final PodSpec podA = TestPodFactory.getPodSpec(
            TASK_A_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-A",
            TASK_A_NAME,
            TASK_A_CMD,
            TestConstants.SERVICE_USER,
            TASK_A_COUNT,
            TASK_A_CPU,
            TASK_A_MEM,
            TASK_A_DISK);

    private static final PodSpec podB = TestPodFactory.getPodSpec(
            TASK_B_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-B",
            TASK_B_NAME,
            TASK_B_CMD,
            TestConstants.SERVICE_USER,
            TASK_B_COUNT,
            TASK_B_CPU,
            TASK_B_MEM,
            TASK_B_DISK);

    private static final PodSpec updatedPodA = TestPodFactory.getPodSpec(
            TASK_A_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-A",
            TASK_A_NAME,
            TASK_A_CMD,
            TestConstants.SERVICE_USER,
            TASK_A_COUNT,
            UPDATED_TASK_A_CPU,
            TASK_A_MEM,
            TASK_A_DISK);

    private static final PodSpec updatedPodB = TestPodFactory.getPodSpec(
            TASK_B_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-B",
            TASK_B_NAME,
            TASK_B_CMD,
            TestConstants.SERVICE_USER,
            TASK_B_COUNT,
            TASK_B_CPU,
            UPDATED_TASK_B_MEM,
            TASK_B_DISK);

    private static final PodSpec invalidPodB = TestPodFactory.getPodSpec(
            TASK_B_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-B",
            TASK_B_NAME,
            TASK_B_CMD,
            TestConstants.SERVICE_USER,
            TASK_B_COUNT - 1,
            TASK_B_CPU,
            TASK_B_MEM,
            TASK_B_DISK);

    private static final PodSpec scaledPodA = TestPodFactory.getPodSpec(
            TASK_A_POD_NAME,
            TestConstants.RESOURCE_SET_ID + "-A",
            TASK_A_NAME,
            TASK_A_CMD,
            TestConstants.SERVICE_USER,
            TASK_A_COUNT + 1,
            TASK_A_CPU,
            TASK_A_MEM,
            TASK_A_DISK);

    private static ServiceSpec getServiceSpec(PodSpec... pods) {
        return DefaultServiceSpec.newBuilder()
                .name(SERVICE_NAME)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("badhost-shouldbeignored:2181")
                .pods(Arrays.asList(pods))
                .user(TestConstants.SERVICE_USER)
                .build();
    }

    private static Capabilities getCapabilities(Boolean enableGpu) throws Exception {
        Capabilities capabilities = new Capabilities(OfferRequirementTestUtils.getTestCluster("1.10-dev"));
        Capabilities mockCapabilities = spy(capabilities);
        when(mockCapabilities.supportsGpuResource()).thenReturn(enableGpu);
        return mockCapabilities;
    }

    private static Capabilities getCapabilitiesWithDefaultGpuSupport() throws Exception {
        return getCapabilities(DEFAULT_GPU_POLICY);
    }

    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;
    private DefaultScheduler defaultScheduler;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockSchedulerFlags.isStateCacheEnabled()).thenReturn(true);
        ServiceSpec serviceSpec = getServiceSpec(podA, podB);
        stateStore = new StateStore(new PersisterCache(new MemPersister()));
        configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), new MemPersister());
        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        defaultScheduler = DefaultScheduler.newBuilder(serviceSpec, flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        defaultScheduler = new TestScheduler(defaultScheduler, true);
        register();
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(defaultScheduler);
    }

    @Test(expected = ConfigStoreException.class)
    public void testConstructConfigStoreWithUnknownCustomType() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                DefaultPodSpec.newBuilder(podA)
                        .placementRule(TestPlacementUtils.PASS)
                        .build());
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(serviceSpecification, Collections.emptyList(), new MemPersister());
    }

    @Test(expected = ConfigStoreException.class)
    public void testConstructConfigStoreWithRegisteredCustomTypeMissingEquals() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                DefaultPodSpec.newBuilder(podA)
                        .placementRule(new PlacementRuleMissingEquality())
                        .build());
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(
                serviceSpecification, Arrays.asList(PlacementRuleMissingEquality.class), new MemPersister());
    }

    @Test(expected = ConfigStoreException.class)
    public void testConstructConfigStoreWithRegisteredCustomTypeBadAnnotations() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                DefaultPodSpec.newBuilder(podA)
                        .placementRule(new PlacementRuleMismatchedAnnotations("hi"))
                        .build());
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(
                serviceSpecification, Arrays.asList(PlacementRuleMismatchedAnnotations.class), new MemPersister());
    }

    @Test
    public void testConstructConfigStoreWithRegisteredGoodCustomType() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                DefaultPodSpec.newBuilder(podA)
                        .placementRule(TestPlacementUtils.PASS)
                        .build());
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(
                serviceSpecification, Arrays.asList(TestPlacementUtils.PASS.getClass()), new MemPersister());
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
        installStep(0, 0, getSufficientOfferForTaskA());

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
    }

    @Test
    public void testLaunchB() throws InterruptedException {
        // Launch A-0
        testLaunchA();
        installStep(1, 0, getSufficientOfferForTaskB());
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
    }

    @Test
    public void testFailLaunchA() throws InterruptedException {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Offer insufficient Resource and wait for step state transition
        UUID offerId = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getInsufficientOfferForTaskA(offerId)));
        defaultScheduler.awaitOffersProcessed();
        Assert.assertEquals(Arrays.asList(Status.PREPARED, Status.PENDING, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
    }

    @Test
    public void updatePerTaskASpecification() throws InterruptedException, IOException, Exception {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitOffersProcessed();
        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        defaultScheduler = DefaultScheduler.newBuilder(getServiceSpec(updatedPodA, podB), flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        register();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.PENDING, Status.COMPLETE, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
    }

    @Test
    public void updatePerTaskBSpecification() throws InterruptedException, IOException, Exception {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitOffersProcessed();
        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        defaultScheduler = DefaultScheduler.newBuilder(getServiceSpec(podA, updatedPodB), flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        register();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
    }

    @Test
    public void updateTaskTypeASpecification() throws InterruptedException, IOException, Exception {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitOffersProcessed();

        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        defaultScheduler = DefaultScheduler.newBuilder(getServiceSpec(scaledPodA, podB), flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        register();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(
                Arrays.asList(Status.COMPLETE, Status.PENDING, Status.COMPLETE, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
    }

    @Test
    public void testInitialLaunchReplaceRecover() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        Assert.assertTrue(stateStore.fetchTaskNames().isEmpty());

        // Launch 1: Task enters ERROR without reaching RUNNING - kill and replace

        // Offer sufficient Resource and wait for its acceptance
        Protos.Offer offer = getSufficientOfferForTaskA();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer.getId())),
                operationsCaptor.capture(),
                any());
        defaultScheduler.awaitOffersProcessed();

        Protos.TaskInfo initialFailedTask = getTask(operationsCaptor.getValue());

        // Task should be initial state
        Protos.TaskStatus status = stateStore.fetchStatus(initialFailedTask.getName()).get();
        Assert.assertTrue(status.toString(), AuxLabelAccess.isInitialLaunch(status));

        // Without sending TASK_RUNNING or any other status, pretend the task failed
        statusUpdate(initialFailedTask.getTaskId(), Protos.TaskState.TASK_ERROR);

        // Expect pod to be killed, initial state to be overwritten, and all tasks to be marked permanently failed
        verify(mockSchedulerDriver, timeout(1000).times(1)).killTask(initialFailedTask.getTaskId());
        Assert.assertFalse(
                AuxLabelAccess.isInitialLaunch(stateStore.fetchStatus(initialFailedTask.getName()).get()));
        Assert.assertTrue(
                new TaskLabelReader(stateStore.fetchTask(initialFailedTask.getName()).get()).isPermanentlyFailed());

        // Launch 2: Task enters ERROR after reaching RUNNING - restart in-place

        // Offer again, and launch successfully this time
        offer = getSufficientOfferForTaskA();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer.getId())),
                operationsCaptor.capture(),
                any());
        defaultScheduler.awaitOffersProcessed();

        Protos.TaskInfo launchedFailedTask = getTask(operationsCaptor.getValue());

        // Task should be in initial state
        status = stateStore.fetchStatus(launchedFailedTask.getName()).get();
        Assert.assertTrue(status.toString(), AuxLabelAccess.isInitialLaunch(status));

        // Sent TASK_RUNNING status
        statusUpdate(launchedFailedTask.getTaskId(), Protos.TaskState.TASK_RUNNING);

        // Check that the step is complete and the task is no longer in initial launch state
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isComplete(), equalTo(true));
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
        status = stateStore.fetchStatus(launchedFailedTask.getName()).get();
        Assert.assertFalse(status.toString(), AuxLabelAccess.isInitialLaunch(status));

        // Now simulate another failure, and verify that the task is NOT marked as permanently failed
        statusUpdate(launchedFailedTask.getTaskId(), Protos.TaskState.TASK_ERROR);
        verify(mockSchedulerDriver, timeout(1000).times(0)).killTask(launchedFailedTask.getTaskId());
        Assert.assertFalse(
                new TaskLabelReader(stateStore.fetchTask(launchedFailedTask.getName()).get()).isPermanentlyFailed());

        // Launch 3: In-place relaunch of last instance

        // Offer again, and check that the task is relaunched as-is
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer.getId())),
                operationsCaptor.capture(),
                any());
        defaultScheduler.awaitOffersProcessed();

        Protos.TaskInfo relaunchedTask = getTask(operationsCaptor.getValue());

        // Not an initial launch.
        status = stateStore.fetchStatus(relaunchedTask.getName()).get();
        Assert.assertFalse(status.toString(), AuxLabelAccess.isInitialLaunch(status));

        // Sent TASK_RUNNING status
        statusUpdate(relaunchedTask.getTaskId(), Protos.TaskState.TASK_RUNNING);

        // Check that the step is complete and the task is still not in initial launch state
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isComplete(), equalTo(true));
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PREPARED, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));
        status = stateStore.fetchStatus(relaunchedTask.getName()).get();
        Assert.assertFalse(status.toString(), AuxLabelAccess.isInitialLaunch(status));

        // Just in case, again verify that killTask() was ONLY called for the initial failed task:
        verify(mockSchedulerDriver, times(1)).killTask(initialFailedTask.getTaskId());
        verify(mockSchedulerDriver, times(0)).killTask(launchedFailedTask.getTaskId());
        verify(mockSchedulerDriver, times(0)).killTask(relaunchedTask.getTaskId());
    }

    @Test
    public void testLaunchAndRecovery() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        Protos.Offer offer1 = getSufficientOfferForTaskA();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer1));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer1.getId())),
                operationsCaptor.capture(),
                any());
        defaultScheduler.awaitOffersProcessed();

        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Protos.TaskID launchedTaskId = getTaskId(operations);

        // Sent TASK_RUNNING status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isComplete(), equalTo(true));
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));

        // Sent TASK_KILLED status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_KILLED);

        reset(mockSchedulerDriver);

        // Make offers sufficient to recover Task A-0 and launch Task B-0,
        // and also have some unused reserved resources for cleaning, and verify that only one of those three happens.

        Protos.Resource cpus = ResourceTestUtils.getExpectedScalar("cpus", 1.0, UUID.randomUUID().toString());
        Protos.Resource mem = ResourceTestUtils.getExpectedScalar("mem", 1.0, UUID.randomUUID().toString());

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
        defaultScheduler.awaitOffersProcessed();

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
                    if (operationSet.iterator().next().getType()
                            == Protos.Offer.Operation.Type.LAUNCH_GROUP) {
                        recovery = true;
                    }
                    break;
                case 2:
                    // Two UNRESERVE operations
                    if (operationSet.stream().allMatch(object -> object.getType()
                            == Protos.Offer.Operation.Type.UNRESERVE)) {
                        recovery = true;
                    }
                    unreserve = true;
                    break;
                case 8: {
                    // Three RESERVE, One CREATE, three RESERVE (for executor) and One LAUNCH operation
                    Map<Protos.Offer.Operation.Type, Integer> expectedCounts = new HashMap<>();
                    expectedCounts.put(Protos.Offer.Operation.Type.RESERVE, 6);
                    expectedCounts.put(Protos.Offer.Operation.Type.CREATE, 1);
                    expectedCounts.put(Protos.Offer.Operation.Type.LAUNCH_GROUP, 1);
                    Map<Protos.Offer.Operation.Type, Integer> operationCounts = new HashMap<>();
                    for (Protos.Offer.Operation operation : operationSet) {
                        Integer count = operationCounts.get(operation.getType());
                        if (count == null) {
                            count = 1;
                        } else {
                            ++count;
                        }
                        operationCounts.put(operation.getType(), count);
                    }
                    if (expectedCounts.equals(operationCounts)) {
                        launch = true;
                    }
                    Assert.assertEquals(operationCounts.toString(), expectedCounts.keySet(), operationCounts.keySet());
                    break;
                }
                default:
                    break;
            }
        }

        Assert.assertTrue(operations.toString(), recovery);
        Assert.assertTrue(operations.toString(), launch);
        Assert.assertTrue(operations.toString(), unreserve);
    }

    @Test
    public void testConfigurationUpdate() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        Protos.Offer offer1 = getSufficientOfferForTaskA();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer1));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer1.getId())),
                operationsCaptor.capture(),
                any());

        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Protos.TaskID launchedTaskId = getTaskId(operations);

        // Send TASK_RUNNING status after the task is Starting (Mesos has been sent Launch)
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isStarting(), equalTo(true));
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isComplete(), equalTo(true));
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                PlanTestUtils.getStepStatuses(plan));

        Assert.assertTrue(stepTaskA0.isComplete());
        Assert.assertEquals(0, defaultScheduler.recoveryPlanManager.getPlan().getChildren().size());

        // Perform Configuration Update
        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        defaultScheduler = DefaultScheduler.newBuilder(getServiceSpec(updatedPodA, podB), flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        defaultScheduler = new TestScheduler(defaultScheduler, true);
        register();
        defaultScheduler.reconciler.forceComplete();
        plan = defaultScheduler.deploymentPlanManager.getPlan();
        stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertEquals(Status.PENDING, stepTaskA0.getStatus());

        List<Protos.Resource> expectedResources = new ArrayList<>(getExpectedResources(operations));
        Protos.Resource neededAdditionalResource = ResourceTestUtils.getUnreservedCpu(UPDATED_TASK_A_CPU - TASK_A_CPU);
        expectedResources.add(neededAdditionalResource);

        // Start update Step
        Protos.Offer insufficientOffer = OfferTestUtils.getCompleteOffer(neededAdditionalResource);
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(insufficientOffer));
        verify(mockSchedulerDriver, timeout(1000).times(1)).killTask(launchedTaskId);
        verify(mockSchedulerDriver, timeout(1000).times(1)).declineOffer(insufficientOffer.getId());
        Assert.assertEquals(Status.PREPARED, stepTaskA0.getStatus());

        // Sent TASK_KILLED status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_KILLED);
        Assert.assertEquals(Status.PREPARED, stepTaskA0.getStatus());
        Assert.assertEquals(0, defaultScheduler.recoveryPlanManager.getPlan().getChildren().size());

        Protos.Offer expectedOffer = OfferTestUtils.getCompleteOffer(expectedResources);
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(expectedOffer));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(expectedOffer.getId())),
                operationsCaptor.capture(),
                any());
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isStarting(), equalTo(true));
        Assert.assertEquals(0, defaultScheduler.recoveryPlanManager.getPlan().getChildren().size());

        operations = operationsCaptor.getValue();
        launchedTaskId = getTaskId(operations);
        // Send TASK_RUNNING status after the task is Starting (Mesos has been sent Launch)
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isStarting(), equalTo(true));
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(stepTaskA0).isComplete(), equalTo(true));
    }

    @Test
    public void testInvalidConfigurationUpdate() throws Exception {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitOffersProcessed();

        // Get initial target config UUID
        UUID targetConfigId = configStore.getTargetConfig();

        // Build new scheduler with invalid config (shrinking task count)
        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        defaultScheduler = DefaultScheduler.newBuilder(getServiceSpec(podA, invalidPodB), flags, new MemPersister())
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();

        // Ensure prior target configuration is still intact
        Assert.assertEquals(targetConfigId, configStore.getTargetConfig());
        Assert.assertEquals(1, defaultScheduler.plans.size());

        Plan deployPlan = defaultScheduler.plans.stream().findAny().get();
        Assert.assertEquals(1, deployPlan.getErrors().size());
    }

    private List<Protos.Resource> getExpectedResources(Collection<Protos.Offer.Operation> operations) {
        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Offer.Operation.Type.LAUNCH_GROUP)) {
                return Stream.concat(
                                operation.getLaunchGroup().getTaskGroup().getTasksList().stream()
                                    .flatMap(taskInfo -> taskInfo.getResourcesList().stream()),
                                operation.getLaunchGroup().getExecutor().getResourcesList().stream())
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    @Test
    public void testSuppress() throws InterruptedException {
        install();
    }

    @Test
    public void testRevive() throws InterruptedException {
        List<Protos.TaskID> taskIds = install();
        statusUpdate(taskIds.get(0), Protos.TaskState.TASK_FAILED);

        Awaitility.await()
            .atMost(1, TimeUnit.SECONDS)
            .until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return !StateStoreUtils.isSuppressed(stateStore);
                }
            });
    }

    @Test
    public void testTaskIpIsStoredOnInstall() throws InterruptedException {
        install();

        // Verify the TaskIP (TaskInfo, strictly speaking) has been stored in the StateStore.
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME ).isPresent());
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_B_POD_NAME + "-0-" + TASK_B_NAME ).isPresent());
    }

    @Test
    public void testTaskIpIsUpdatedOnStatusUpdate() throws InterruptedException {
        List<Protos.TaskID> taskIds = install();
        String updateIp = "1.1.1.1";

        // Verify the TaskIP (TaskInfo, strictly speaking) has been stored in the StateStore.
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME ).isPresent());
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_B_POD_NAME + "-0-" + TASK_B_NAME ).isPresent());

        Protos.TaskStatus update = Protos.TaskStatus.newBuilder(
                getTaskStatus(taskIds.get(0), Protos.TaskState.TASK_STAGING))
                .setContainerStatus(Protos.ContainerStatus.newBuilder()
                        .addNetworkInfos(Protos.NetworkInfo.newBuilder()
                            .addIpAddresses(Protos.NetworkInfo.IPAddress.newBuilder()
                                .setIpAddress(updateIp))))
                .build();
        defaultScheduler.statusUpdate(mockSchedulerDriver, update);

        // Verify the TaskStatus was update.
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME ).isPresent());

        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> {
            return StateStoreUtils.getTaskStatusFromProperty(
                    stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME ).get()
                    .getContainerStatus()
                    .getNetworkInfos(0)
                    .getIpAddresses(0)
                    .getIpAddress().equals(updateIp);
        });
    }

    @Test
    public void testTaskIpIsNotOverwrittenByEmptyOnUpdate() throws InterruptedException {
        List<Protos.TaskID> taskIds = install();

        // Verify the TaskIP (TaskInfo, strictly speaking) has been stored in the StateStore.
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME ).isPresent());
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_B_POD_NAME + "-0-" + TASK_B_NAME ).isPresent());

        Protos.TaskStatus update = Protos.TaskStatus.newBuilder(
                getTaskStatus(taskIds.get(0), Protos.TaskState.TASK_STAGING))
                .setContainerStatus(Protos.ContainerStatus.newBuilder()
                        .addNetworkInfos(Protos.NetworkInfo.newBuilder()))
                .build();
        defaultScheduler.statusUpdate(mockSchedulerDriver, update);

        // Verify the TaskStatus was NOT updated.
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME ).isPresent());

        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> {
            return StateStoreUtils.getTaskStatusFromProperty(
                    stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME ).get()
                    .getContainerStatus()
                    .getNetworkInfos(0)
                    .getIpAddresses(0)
                    .getIpAddress().equals(TASK_IP);
        });
    }

    @Test
    public void testApiServerNotReadyDecline() {
        TestScheduler testScheduler = new TestScheduler(defaultScheduler, false);
        testScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getSufficientOfferForTaskA()));
        verify(mockSchedulerDriver, timeout(1000).times(1)).declineOffer(any());
    }

    @Test
    public void testOverrideDeployWithUpdate() {
        Collection<Plan> plans = getDeployUpdatePlans();
        ConfigurationUpdater.UpdateResult updateResult = mock(ConfigurationUpdater.UpdateResult.class);
        when(updateResult.getDeploymentType()).thenReturn(ConfigurationUpdater.UpdateResult.DeploymentType.UPDATE);
        plans = DefaultScheduler.Builder.overrideDeployPlan(plans, updateResult);

        Plan deployPlan = plans.stream()
                .filter(plan -> plan.getName().equals(Constants.DEPLOY_PLAN_NAME))
                .findFirst().get();

        Assert.assertEquals(1, deployPlan.getChildren().size());
    }

    @Test
    public void testNoOverrideOfDeployPlanOnInstall() {
        Collection<Plan> plans = getDeployUpdatePlans();
        ConfigurationUpdater.UpdateResult updateResult = mock(ConfigurationUpdater.UpdateResult.class);
        when(updateResult.getDeploymentType()).thenReturn(ConfigurationUpdater.UpdateResult.DeploymentType.DEPLOY);
        plans = DefaultScheduler.Builder.overrideDeployPlan(plans, updateResult);

        Plan deployPlan = plans.stream()
                .filter(plan -> plan.getName().equals(Constants.DEPLOY_PLAN_NAME))
                .findFirst().get();

        Assert.assertEquals(2, deployPlan.getChildren().size());
    }

    @Test
    public void testGetLaunchableTasks() {
        Set<String> launchableTasks = defaultScheduler.getLaunchableTasks();
        Assert.assertArrayEquals(new String[]{"POD-A-0-A", "POD-B-0-B", "POD-B-1-B"}, launchableTasks.toArray());
    }

    // Deploy plan has 2 phases, update plan has 1 for distinguishing which was chosen.
    private Collection<Plan> getDeployUpdatePlans() {
        Phase phase = mock(Phase.class);
        Plan deployPlan = mock(Plan.class);
        when(deployPlan.getName()).thenReturn(Constants.DEPLOY_PLAN_NAME);
        when(deployPlan.getChildren()).thenReturn(Arrays.asList(phase, phase));

        Plan updatePlan = mock(Plan.class);
        when(updatePlan.getName()).thenReturn(Constants.UPDATE_PLAN_NAME);
        when(updatePlan.getChildren()).thenReturn(Arrays.asList(phase));

        Assert.assertEquals(2, deployPlan.getChildren().size());
        Assert.assertEquals(1, updatePlan.getChildren().size());
        return Arrays.asList(deployPlan, updatePlan);
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

    private static Protos.TaskInfo getTask(Collection<Protos.Offer.Operation> operations) {
        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Offer.Operation.Type.LAUNCH_GROUP)) {
                return operation.getLaunchGroup().getTaskGroup().getTasks(0);
            }
        }

        return null;
    }

    private static Protos.TaskID getTaskId(Collection<Protos.Offer.Operation> operations) {
        return getTask(operations).getTaskId();
    }

    private static Protos.TaskStatus getTaskStatus(Protos.TaskID taskID, Protos.TaskState state) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(state)
                .setContainerStatus(Protos.ContainerStatus.newBuilder()
                    .addNetworkInfos(Protos.NetworkInfo.newBuilder()
                        .addIpAddresses(Protos.NetworkInfo.IPAddress.newBuilder()
                            .setIpAddress(TASK_IP))))
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
                                ResourceTestUtils.getUnreservedCpu(TASK_A_CPU + 0.1),
                                ResourceTestUtils.getUnreservedMem(TASK_A_MEM + 32),
                                ResourceTestUtils.getUnreservedDisk(TASK_A_DISK + 256)))
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
                                ResourceTestUtils.getUnreservedCpu(TASK_B_CPU + 0.1),
                                ResourceTestUtils.getUnreservedMem(TASK_B_MEM + 32),
                                ResourceTestUtils.getUnreservedDisk(TASK_B_DISK + 256)))
                .build();
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

    private Protos.TaskID installStep(int phaseIndex, int stepIndex, Protos.Offer offer) {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        List<Protos.Offer> offers = Arrays.asList(offer);
        Protos.OfferID offerId = offer.getId();
        Step step = plan.getChildren().get(phaseIndex).getChildren().get(stepIndex);
        Assert.assertTrue(step.isPending());

        // Offer sufficient Resource and wait for its acceptance
        defaultScheduler.resourceOffers(mockSchedulerDriver, offers);
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                Matchers.argThat(isACollectionThat(contains(offerId))),
                operationsCaptor.capture(),
                any());

        // Verify 2 Reserve and 1 Launch Operations were executed
        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Assert.assertEquals(8, operations.size());
        Assert.assertEquals(6, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, operations));
        Assert.assertEquals(1, countOperationType(Offer.Operation.Type.LAUNCH_GROUP, operations));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(Awaitility.to(step).isStarting(), equalTo(true));

        // Sent TASK_RUNNING status
        Protos.TaskID taskId = getTaskId(operations);
        statusUpdate(getTaskId(operations), Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilCall(Awaitility.to(step).isComplete(), equalTo(true));

        return taskId;
    }

    /**
     * Workaround for typecast warnings relating to Collection arguments.
     * @see https://stackoverflow.com/questions/20441594/mockito-and-hamcrest-how-to-verify-invokation-of-collection-argument
     */
    private static <T> Matcher<Collection<T>> isACollectionThat(final Matcher<Iterable<? extends T>> matcher) {
        return new BaseMatcher<Collection<T>>() {
            @Override public boolean matches(Object item) {
                return matcher.matches(item);
            }
            @Override public void describeTo(Description description) {
                matcher.describeTo(description);
            }
        };
    }

    private void statusUpdate(Protos.TaskID launchedTaskId, Protos.TaskState state) {
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, state);
        defaultScheduler.statusUpdate(mockSchedulerDriver, runningStatus);
    }

    //Installs the service.
    private List<Protos.TaskID> install() throws InterruptedException {
        List<Protos.TaskID> taskIds = new ArrayList<>();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        taskIds.add(installStep(0, 0, getSufficientOfferForTaskA()));
        taskIds.add(installStep(1, 0, getSufficientOfferForTaskB()));
        taskIds.add(installStep(1, 1, getSufficientOfferForTaskB()));
        defaultScheduler.awaitOffersProcessed();

        Assert.assertTrue(defaultScheduler.deploymentPlanManager.getPlan().isComplete());
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE),
                PlanTestUtils.getStepStatuses(plan));
        Awaitility.await()
                .atMost(
                        SuppressReviveManager.SUPPRESSS_REVIVE_DELAY_S +
                        SuppressReviveManager.SUPPRESSS_REVIVE_INTERVAL_S + 1,
                        TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return StateStoreUtils.isSuppressed(stateStore);
                    }
                });

        return taskIds;
    }

    private static class PlacementRuleMissingEquality implements PlacementRule {
        @Override
        public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "test pass").build();
        }
    }

    private static class PlacementRuleMismatchedAnnotations implements PlacementRule {

        private final String fork;

        @JsonCreator
        PlacementRuleMismatchedAnnotations(@JsonProperty("wrong") String spoon) {
            this.fork = spoon;
        }

        @Override
        public EvaluationOutcome filter(Offer offer, PodInstance podInstance, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "test pass").build();
        }

        @JsonProperty("message")
        private String getMsg() {
            return fork;
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }

    private static class TestScheduler extends DefaultScheduler {
        private final boolean apiServerReady;

        public TestScheduler(DefaultScheduler defaultScheduler, boolean apiServerReady) {
            super(
                    defaultScheduler.serviceSpec,
                    flags,
                    defaultScheduler.resources,
                    defaultScheduler.plans,
                    defaultScheduler.stateStore,
                    defaultScheduler.configStore,
                    defaultScheduler.customEndpointProducers,
                    defaultScheduler.recoveryPlanOverriderFactory);
            this.apiServerReady = apiServerReady;
        }

        @Override
        public boolean apiServerReady() {
            return apiServerReady;
        }
    }
}
