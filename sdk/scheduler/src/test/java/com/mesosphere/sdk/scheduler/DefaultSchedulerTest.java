package com.mesosphere.sdk.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.TestPlacementUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import com.mesosphere.sdk.testutils.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.awaitility.Awaitility;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.mockito.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.generateServiceSpec;
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
    @ClassRule public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @Rule public TestRule globalTimeout = new DisableOnDebug(new Timeout(10, TimeUnit.SECONDS));

    @Mock private SchedulerDriver mockSchedulerDriver;
    @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;
    @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor2;

    private static TestingServer testingServer;
    private ServiceSpec SERVICE_SPEC;
    private ServiceSpec UPDATED_POD_A_SERVICE_SPEC;
    private ServiceSpec UPDATED_POD_B_SERVICE_SPEC;
    private ServiceSpec SCALED_POD_A_SERVICE_SPEC;
    private static final String POD_A_TYPE = "pod-a";
    private static final String POD_B_TYPE = "pod-b";

    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;
    private DefaultScheduler defaultScheduler;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);
        StateStoreCache.resetInstanceForTests();

        SERVICE_SPEC = generateServiceSpec(getRawServiceSpec());
        UPDATED_POD_A_SERVICE_SPEC = generateServiceSpec(getRawServiceSpecUpdateA());
        UPDATED_POD_B_SERVICE_SPEC = generateServiceSpec(getRawServiceSpecUpdateB());
        SCALED_POD_A_SERVICE_SPEC = generateServiceSpec(getRawServiceSpecScaleA());

        stateStore = DefaultScheduler.createStateStore(SERVICE_SPEC, testingServer.getConnectString());
        configStore = DefaultScheduler.createConfigStore(SERVICE_SPEC, testingServer.getConnectString());
        defaultScheduler = DefaultScheduler.newBuilder(SERVICE_SPEC)
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();

        register();
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(defaultScheduler);
    }

    @Test(expected = ConfigStoreException.class)
    public void testConstructConfigStoreWithUnknownCustomType() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                SERVICE_SPEC.getName(),
                DefaultPodSpec.newBuilder(getPodSpec(SERVICE_SPEC, POD_A_TYPE))
                        .placementRule(TestPlacementUtils.PASS)
                        .build())
                .build();
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(serviceSpecification, testingServer.getConnectString());
    }

    @Test(expected = ConfigStoreException.class)
    public void testConstructConfigStoreWithRegisteredCustomTypeMissingEquals() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                SERVICE_SPEC.getName(),
                DefaultPodSpec.newBuilder(getPodSpec(SERVICE_SPEC, POD_A_TYPE))
                        .placementRule(new PlacementRuleMissingEquality())
                        .build())
                .build();
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(
                serviceSpecification,
                testingServer.getConnectString(),
                Arrays.asList(PlacementRuleMissingEquality.class));
    }

    @Test(expected = ConfigStoreException.class)
    public void testConstructConfigStoreWithRegisteredCustomTypeBadAnnotations() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                SERVICE_SPEC.getName(),
                DefaultPodSpec.newBuilder(getPodSpec(SERVICE_SPEC, POD_A_TYPE))
                        .placementRule(new PlacementRuleMismatchedAnnotations("hi"))
                        .build())
                .build();
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(
                serviceSpecification,
                testingServer.getConnectString(),
                Arrays.asList(PlacementRuleMismatchedAnnotations.class));
    }

    @Test
    public void testConstructConfigStoreWithRegisteredGoodCustomType() throws ConfigStoreException {
        ServiceSpec serviceSpecification = getServiceSpec(
                SERVICE_SPEC.getName(),
                DefaultPodSpec.newBuilder(getPodSpec(SERVICE_SPEC, POD_A_TYPE))
                        .placementRule(TestPlacementUtils.PASS)
                        .build())
                .build();
        Assert.assertTrue(serviceSpecification.getPods().get(0).getPlacementRule().isPresent());
        DefaultScheduler.createConfigStore(
                serviceSpecification,
                testingServer.getConnectString(),
                Arrays.asList(TestPlacementUtils.PASS.getClass()));
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
        installStep(0, 0, getSufficientOfferForTaskA(SERVICE_SPEC));

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getStepStatuses(plan));
    }

    @Test
    public void testLaunchB() throws InterruptedException {
        // Launch A-0
        testLaunchA();
        installStep(1, 0, getSufficientOfferForTaskB(SERVICE_SPEC));

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.PENDING), getStepStatuses(plan));
    }

    @Test
    public void testFailLaunchA() throws InterruptedException {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        UUID offerId = UUID.randomUUID();
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getInsufficientOfferForTaskA(SERVICE_SPEC, offerId)));
        defaultScheduler.awaitTermination();
        Assert.assertEquals(Arrays.asList(Status.PREPARED, Status.PENDING, Status.PENDING), getStepStatuses(plan));
    }

    @Test
    public void updatePerTaskASpecification() throws InterruptedException, IOException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();
        defaultScheduler = DefaultScheduler.newBuilder(UPDATED_POD_A_SERVICE_SPEC)
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        register();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.PENDING, Status.COMPLETE, Status.PENDING), getStepStatuses(plan));
    }

    @Test
    public void updatePerTaskBSpecification() throws InterruptedException, IOException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();
        defaultScheduler = DefaultScheduler.newBuilder(UPDATED_POD_B_SERVICE_SPEC)
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        register();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getStepStatuses(plan));
    }

    @Test
    public void updateTaskTypeASpecification() throws InterruptedException, IOException {
        // Launch A and B in original configuration
        testLaunchB();
        defaultScheduler.awaitTermination();

        defaultScheduler = DefaultScheduler.newBuilder(SCALED_POD_A_SERVICE_SPEC)
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        register();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.COMPLETE, Status.PENDING), getStepStatuses(plan));
    }

    @Test
    public void testLaunchAndRecovery() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        Protos.Offer offer1 = getSufficientOfferForTaskA(SERVICE_SPEC);
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer1));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer1.getId())),
                operationsCaptor.capture(),
                any());

        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Protos.TaskID launchedTaskId = getTaskId(operations);

        // Sent TASK_RUNNING status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(stepTaskA0).isComplete(), equalTo(true));
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getStepStatuses(plan));

        // Sent TASK_KILLED status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_KILLED);

        reset(mockSchedulerDriver);

        // Make offers sufficient to recover Task A-0 and launch Task B-0,
        // and also have some unused reserved resources for cleaning, and verify that only one of those three happens.
        Protos.Resource cpus = ResourceTestUtils.getDesiredCpu(1.0);
        cpus = ResourceUtils.setResourceId(cpus, UUID.randomUUID().toString());
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(1.0);
        mem = ResourceUtils.setResourceId(mem, UUID.randomUUID().toString());

        Protos.Offer offerA = Protos.Offer.newBuilder(getSufficientOfferForTaskA(SERVICE_SPEC))
                .addAllResources(operations.stream()
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();
        Protos.Offer offerB = Protos.Offer.newBuilder(getSufficientOfferForTaskB(SERVICE_SPEC))
                .addAllResources(operations.stream()
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();
        Protos.Offer offerC = Protos.Offer.newBuilder(getSufficientOfferForTaskB(SERVICE_SPEC))
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
                    if (operationSet.iterator().next().getType()
                            == Protos.Offer.Operation.Type.LAUNCH) {
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
    public void testConfigurationUpdate() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Offer sufficient Resource and wait for its acceptance
        Protos.Offer offer1 = getSufficientOfferForTaskA(SERVICE_SPEC);
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(offer1));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(offer1.getId())),
                operationsCaptor.capture(),
                any());

        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Protos.TaskID launchedTaskId = getTaskId(operations);

        // Sent TASK_RUNNING status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(stepTaskA0).isComplete(), equalTo(true));
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING), getStepStatuses(plan));

        Assert.assertTrue(stepTaskA0.isComplete());
        Assert.assertEquals(1, defaultScheduler.recoveryPlanManager.getPlan().getChildren().size());
        Assert.assertTrue(defaultScheduler.recoveryPlanManager.getPlan().getChildren().get(0).getChildren().isEmpty());

        // Perform Configuration Update
        defaultScheduler = DefaultScheduler.newBuilder(UPDATED_POD_A_SERVICE_SPEC)
                .setStateStore(stateStore)
                .setConfigStore(configStore)
                .build();
        register();
        defaultScheduler.reconciler.forceComplete();
        plan = defaultScheduler.deploymentPlanManager.getPlan();
        stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertEquals(Status.PENDING, stepTaskA0.getStatus());

        List<Protos.Resource> expectedResources = getExpectedResources(operations);
        double updatedTaskACpu = getResourceValue(getPodSpec(UPDATED_POD_A_SERVICE_SPEC, POD_A_TYPE), "cpus");
        double taskACpu = getResourceValue(getPodSpec(SERVICE_SPEC, POD_A_TYPE), "cpus");
        Protos.Resource neededAdditionalResource = ResourceTestUtils.getUnreservedCpu(updatedTaskACpu - taskACpu);
        expectedResources.add(neededAdditionalResource);

        // Start update Step
        Protos.Offer insufficientOffer = OfferTestUtils.getOffer(neededAdditionalResource);
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(insufficientOffer));
        verify(mockSchedulerDriver, timeout(1000).times(1)).killTask(launchedTaskId);
        verify(mockSchedulerDriver, timeout(1000).times(1)).declineOffer(insufficientOffer.getId());
        Assert.assertEquals(Status.PREPARED, stepTaskA0.getStatus());

        // Sent TASK_KILLED status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_KILLED);
        Assert.assertEquals(Status.PREPARED, stepTaskA0.getStatus());
        Assert.assertEquals(1, defaultScheduler.recoveryPlanManager.getPlan().getChildren().size());
        Assert.assertTrue(defaultScheduler.recoveryPlanManager.getPlan().getChildren().get(0).getChildren().isEmpty());

        Protos.Offer expectedOffer = OfferTestUtils.getOffer(expectedResources);
        defaultScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(expectedOffer));
        verify(mockSchedulerDriver, timeout(1000).times(1)).acceptOffers(
                collectionThat(contains(expectedOffer.getId())),
                operationsCaptor.capture(),
                any());
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(stepTaskA0).isStarting(), equalTo(true));
        Assert.assertEquals(1, defaultScheduler.recoveryPlanManager.getPlan().getChildren().size());
        Assert.assertTrue(defaultScheduler.recoveryPlanManager.getPlan().getChildren().get(0).getChildren().isEmpty());

        operations = operationsCaptor.getValue();
        launchedTaskId = getTaskId(operations);
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(stepTaskA0).isComplete(), equalTo(true));
    }

    private List<Protos.Resource> getExpectedResources(Collection<Protos.Offer.Operation> operations) {
        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                return operation.getLaunch().getTaskInfosList().stream()
                        .flatMap(taskInfo -> taskInfo.getResourcesList().stream())
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
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

    private Protos.Offer getSufficientOfferForTaskA(ServiceSpec serviceSpec) {
        return getSufficientOffer(serviceSpec, POD_A_TYPE);
    }

    private Protos.Offer getSufficientOfferForTaskB(ServiceSpec serviceSpec) {
        return getSufficientOffer(serviceSpec, POD_B_TYPE);
    }

    private Protos.Offer getSufficientOffer(ServiceSpec serviceSpec, String podType) {
        return getScaledOffer(serviceSpec, podType, 1.0);
    }

    private Protos.Offer getInsufficientOfferForTaskA(ServiceSpec serviceSpec, UUID offerId) {
        return getScaledOffer(serviceSpec, POD_A_TYPE, 0.5).toBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()))
                .build();
    }

    private Protos.Offer getScaledOffer(ServiceSpec serviceSpec, String podType, double scale) {
        UUID offerId = UUID.randomUUID();
        PodSpec podSpec = getPodSpec(serviceSpec, podType);
        double cpu = getResourceValue(podSpec, "cpus");
        double mem = getResourceValue(podSpec, "mem");
        double disk = getVolumeValue(podSpec);

        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpu(cpu * scale),
                                ResourceTestUtils.getUnreservedMem(mem * scale),
                                ResourceTestUtils.getUnreservedDisk(disk * scale)))
                .build();
    }

    private PodSpec getPodSpec(ServiceSpec serviceSpec, String podType) {
        return serviceSpec.getPods().stream()
                .filter(podSpec -> podSpec.getType().equals(podType))
                .findAny()
                .get();
    }

    private double getResourceValue(PodSpec podSpec, String resourceName) {
        return podSpec.getResources().stream().findAny().get()
                .getResources().stream()
                .filter(resourceSpec -> resourceSpec.getName().equals(resourceName))
                .findAny().get()
                .getValue()
                .getScalar()
                .getValue();
    }

    /**
     * Returns a volume's disk size from the PodSpec.  Assumes there is a single volume in the PodSpec to get meaningful
     * results.
     */
    private double getVolumeValue(PodSpec podSpec) {
        return podSpec.getResources().stream().findAny().get()
                .getVolumes().stream()
                .findAny().get()
                .getValue()
                .getScalar()
                .getValue();
    }


    private static List<Status> getStepStatuses(Plan plan) {
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .map(step -> step.getStatus())
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
                (Collection<Protos.OfferID>) Matchers.argThat(contains(offerId)),
                operationsCaptor.capture(),
                any());

        // Verify 2 Reserve and 1 Launch Operations were executed
        Collection<Protos.Offer.Operation> operations = operationsCaptor.getValue();
        Assert.assertEquals(5, operations.size());
        Assert.assertEquals(3, countOperationType(Protos.Offer.Operation.Type.RESERVE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, operations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.LAUNCH, operations));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(step).isStarting(), equalTo(true));

        // Sent TASK_RUNNING status
        Protos.TaskID taskId = getTaskId(operations);
        statusUpdate(getTaskId(operations), Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(step).isComplete(), equalTo(true));

        return taskId;
    }

    private void statusUpdate(Protos.TaskID launchedTaskId, Protos.TaskState state) {
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, state);
        defaultScheduler.statusUpdate(mockSchedulerDriver, runningStatus);
    }

    /**
     * Installs the service.
     */
    private List<Protos.TaskID> install() {
        List<Protos.TaskID> taskIds = new ArrayList<>();

        Plan plan = defaultScheduler.deploymentPlanManager.getPlan();
        taskIds.add(installStep(0, 0, getSufficientOfferForTaskA(SERVICE_SPEC)));
        taskIds.add(installStep(1, 0, getSufficientOfferForTaskB(SERVICE_SPEC)));
        taskIds.add(installStep(1, 1, getSufficientOfferForTaskB(SERVICE_SPEC)));

        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE), getStepStatuses(plan));
        Assert.assertTrue(stateStore.isSuppressed());

        return taskIds;
    }

    private static class PlacementRuleMissingEquality implements PlacementRule {
        @Override
        public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement,
                            Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "test pass");
        }
    }

    private static class PlacementRuleMismatchedAnnotations implements PlacementRule {

        private final String fork;

        @JsonCreator
        PlacementRuleMismatchedAnnotations(@JsonProperty("wrong") String spoon) {
            this.fork = spoon;
        }

        @Override
        public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement,
                            Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "test pass");
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

    /**
     * test-scheduler.yml
     *
     * name: "test"
     * pods:
     *   pod-a:
     *   count: 1
     *   resource-sets:
     *     resource-set-a:
     *       cpus: 1
     *       memory: 1000
     *       volume:
     *         path: test-container-path
     *         type: ROOT
     *         size: 1500
     *   tasks:
     *     a:
     *       goal: RUNNING
     *       cmd: echo a
     *       resource-set: resource-set-a
     *   pod-b:
     *     count: 2
     *     resource-sets:
     *       resource-set-b:
     *         cpus: 2
     *         memory: 2000
     *         volume:
     *           path: test-container-path
     *           type: ROOT
     *           size: 2500
     *     tasks:
     *       b:
     *         goal: RUNNING
     *         cmd: echo b
     *         resource-set: resource-set-b
     */
    private RawServiceSpec getRawServiceSpec() {
        WriteOnceLinkedHashMap<String, RawPod> pods =  new WriteOnceLinkedHashMap<>();
        pods.put("pod-a", getRawPod("a", 1.0, 1000, 1));
        pods.put("pod-b", getRawPod("b", 2.0, 2000, 2));
        return getRawServiceSpec(pods);
    }

    private RawServiceSpec getRawServiceSpecUpdateA() {
        WriteOnceLinkedHashMap<String, RawPod> pods =  new WriteOnceLinkedHashMap<>();
        pods.put("pod-a", getRawPod("a", 2.0, 1000, 1)); // From 1.0 cpus to 2.0 cpus
        pods.put("pod-b", getRawPod("b", 2.0, 2000, 2));
        return getRawServiceSpec(pods);
    }

    private RawServiceSpec getRawServiceSpecUpdateB() {
        WriteOnceLinkedHashMap<String, RawPod> pods =  new WriteOnceLinkedHashMap<>();
        pods.put("pod-a", getRawPod("a", 1.0, 1000, 1));
        pods.put("pod-b", getRawPod("b", 2.0, 4000, 2)); // From 2000 mem to 4000 mem
        return getRawServiceSpec(pods);
    }

    private RawServiceSpec getRawServiceSpecScaleA() {
        WriteOnceLinkedHashMap<String, RawPod> pods =  new WriteOnceLinkedHashMap<>();
        pods.put("pod-a", getRawPod("a", 1.0, 1000, 2)); // From 1 count to 2 count
        pods.put("pod-b", getRawPod("b", 2.0, 2000, 2));
        return getRawServiceSpec(pods);
    }

    private RawServiceSpec getRawServiceSpec(WriteOnceLinkedHashMap<String, RawPod> pods) {
        return RawServiceSpec.newBuilder()
                .name("test")
                .pods(pods)
                .build();
    }

    private RawPod getRawPod(String name, double cpus, int memory, int count) {
        WriteOnceLinkedHashMap<String, RawTask> task =  new WriteOnceLinkedHashMap<>();
        task.put(
                name,
                RawTask.newBuilder()
                        .goal("RUNNING")
                        .cmd("echo " + name)
                        .resourceSet("resource-set-" + name)
                        .build());

        WriteOnceLinkedHashMap<String, RawResourceSet> resourceSet =  new WriteOnceLinkedHashMap<>();
        resourceSet.put(
                "resource-set-" + name,
                RawResourceSet.newBuilder()
                        .cpus(cpus)
                        .memory(memory)
                        .volume(RawVolume.newBuilder()
                                .path("test-container-path")
                                .type("ROOT")
                                .size(1500)
                                .build())
                        .build());

        return RawPod.newBuilder()
                .count(count)
                .tasks(task)
                .resourceSets(resourceSet)
                .build();
    }

    private static final DefaultServiceSpec.Builder getServiceSpec(String serviceName, PodSpec... pods) {
        return DefaultServiceSpec.newBuilder()
                .name(serviceName)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .apiPort(0)
                .zookeeperConnection("foo.bar.com")
                .pods(Arrays.asList(pods));
    }
}
