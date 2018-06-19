package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosVersion;
import com.mesosphere.sdk.framework.Driver;
import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.MesosEventClient.OfferResponse;
import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.scheduler.MesosEventClient.UnexpectedResourcesResponse;
import com.mesosphere.sdk.scheduler.decommission.DecommissionPlanFactory;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.uninstall.UninstallRecorder;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.PersistentLaunchRecorder;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.SchedulerDriver;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.mockito.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mesosphere.sdk.dcos.DcosConstants.DEFAULT_GPU_POLICY;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * This class tests the {@link DefaultScheduler} class.
 */
@SuppressWarnings({"PMD.TooManyStaticImports", "PMD.AvoidUsingHardCodedIP"})
public class DefaultSchedulerTest {
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @Rule public TestRule globalTimeout = new DisableOnDebug(new Timeout(30, TimeUnit.SECONDS));
    @Mock private SchedulerDriver mockSchedulerDriver;
    @Mock private SchedulerConfig mockSchedulerConfig;

    private static final String TASK_IP = "9.9.9.9";

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
                .name(TestConstants.SERVICE_NAME)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("badhost-shouldbeignored:2181")
                .pods(Arrays.asList(pods))
                .user(TestConstants.SERVICE_USER)
                .build();
    }

    private Capabilities getCapabilities() throws Exception {
        return new Capabilities(new DcosVersion("1.10-dev", DcosVersion.DcosVariant.UNKNOWN)) {
            @Override
            public boolean supportsGpuResource() {
                return DEFAULT_GPU_POLICY;
            }

            @Override
            public boolean supportsDomains() {
                return true;
            }
        };
    }

    private Persister persister;
    private DefaultScheduler defaultScheduler;

    @BeforeClass
    public static void beforeAll() {
        // Disable background TaskKiller thread, to avoid erroneous kill invocations
        try {
            TaskKiller.reset(false);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterClass
    public static void afterAll() {
        // Re-enable TaskKiller thread
        try {
            TaskKiller.reset(false);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(mockSchedulerDriver);

        when(mockSchedulerConfig.isStateCacheEnabled()).thenReturn(true);
        ServiceSpec serviceSpec = getServiceSpec(podA, podB);
        Capabilities.overrideCapabilities(getCapabilities());
        persister = new MemPersister();
        // Emulate behavior of upstream FrameworkScheduler, which handled registering with Mesos:
        new FrameworkStore(persister).storeFrameworkId(TestConstants.FRAMEWORK_ID);
        defaultScheduler = getScheduler(serviceSpec);
    }

    @Test
    public void testEmptyOffers() {
        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(true), defaultScheduler.getClientStatus());

        // Reconcile already triggered via registration during setup:
        OfferResponse offerResponse = defaultScheduler.offers(Collections.emptyList());
        Assert.assertEquals(OfferResponse.Result.PROCESSED, offerResponse.result);
        Assert.assertTrue(offerResponse.recommendations.isEmpty());
    }

    @Test
    public void testLaunchA() throws Exception {
        installStep(0, 0, getSufficientOfferForTaskA(), Status.PENDING, true);
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                getStepStatuses(getDeploymentPlan()));
    }

    @Test
    public void testLaunchB() throws Exception {
        // Launch A-0
        testLaunchA();

        installStep(1, 0, getSufficientOfferForTaskB(), Status.PENDING, true);
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.PENDING),
                getStepStatuses(getDeploymentPlan()));
    }

    @Test
    public void testFailLaunchA() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = getDeploymentPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(true), defaultScheduler.getClientStatus());

        // Offer insufficient Resource and wait for step state transition
        UUID offerId = UUID.randomUUID();
        defaultScheduler.offers(Collections.singletonList(getInsufficientOfferForTaskA(offerId)));
        Assert.assertEquals(Arrays.asList(Status.PREPARED, Status.PENDING, Status.PENDING),
                getStepStatuses(plan));
    }

    @Test
    public void updatePerTaskASpecification() throws InterruptedException, IOException, Exception {
        // Launch A and B in original configuration
        testLaunchB();
        Capabilities.overrideCapabilities(getCapabilities());
        defaultScheduler = getScheduler(getServiceSpec(updatedPodA, podB));

        Assert.assertEquals(Arrays.asList(Status.PENDING, Status.COMPLETE, Status.PENDING),
                getStepStatuses(getDeploymentPlan()));
    }

    @Test
    public void updatePerTaskBSpecification() throws InterruptedException, IOException, Exception {
        // Launch A and B in original configuration
        testLaunchB();
        Capabilities.overrideCapabilities(getCapabilities());
        defaultScheduler = getScheduler(getServiceSpec(podA, updatedPodB));

        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                getStepStatuses(getDeploymentPlan()));
    }

    @Test
    public void updateTaskTypeASpecification() throws InterruptedException, IOException, Exception {
        // Launch A and B in original configuration
        testLaunchB();

        Capabilities.overrideCapabilities(getCapabilities());
        defaultScheduler = getScheduler(getServiceSpec(scaledPodA, podB));

        Assert.assertEquals(
                Arrays.asList(Status.COMPLETE, Status.PENDING, Status.COMPLETE, Status.PENDING),
                getStepStatuses(getDeploymentPlan()));
    }

    @Test
    public void testLaunchAndRecovery() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = getDeploymentPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(true), defaultScheduler.getClientStatus());

        // Offer sufficient Resources and wait for its acceptance
        Protos.Offer offer1 = getSufficientOfferForTaskA();
        OfferResponse response = defaultScheduler.offers(Collections.singletonList(offer1));
        Assert.assertEquals(8, response.recommendations.size());
        Assert.assertEquals(offer1, response.recommendations.iterator().next().getOffer());

        Protos.TaskID launchedTaskId = getTaskId(response.recommendations);

        // Sent TASK_RUNNING status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Assert.assertTrue(stepTaskA0.isComplete());
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                getStepStatuses(plan));

        // Sent TASK_KILLED status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_KILLED);

        reset(mockSchedulerDriver);

        // Make offers sufficient to recover Task A-0 and launch Task B-0,
        // and also have some unused reserved resources for cleaning, and verify that only one of those three happens.

        Protos.Resource cpus = ResourceTestUtils.getReservedCpus(1.0, UUID.randomUUID().toString());
        Protos.Resource mem = ResourceTestUtils.getReservedMem(1.0, UUID.randomUUID().toString());

        Protos.Offer offerA = Protos.Offer.newBuilder(getSufficientOfferForTaskA())
                .addAllResources(response.recommendations.stream()
                        .map(rec -> rec.getOperation())
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();
        Protos.Offer offerB = Protos.Offer.newBuilder(getSufficientOfferForTaskB())
                .addAllResources(response.recommendations.stream()
                        .map(rec -> rec.getOperation())
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();
        Protos.Offer offerC = Protos.Offer.newBuilder(getSufficientOfferForTaskB())
                .addAllResources(response.recommendations.stream()
                        .map(rec -> rec.getOperation())
                        .filter(Protos.Offer.Operation::hasReserve)
                        .flatMap(operation -> operation.getReserve().getResourcesList().stream())
                        .collect(Collectors.toList()))
                .addResources(cpus)
                .addResources(mem)
                .build();

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(true), defaultScheduler.getClientStatus());

        Collection<Protos.Offer> offers = Arrays.asList(offerA, offerB, offerC);
        response = defaultScheduler.offers(offers);

        // Only offerA/offerB are consumed:
        Assert.assertEquals(Arrays.asList(offerC), OfferUtils.filterOutAcceptedOffers(offers, response.recommendations));
        Assert.assertEquals(new HashSet<>(Arrays.asList(offerA.getId(), offerB.getId())),
                response.recommendations.stream().map(r -> r.getOffer().getId()).distinct().collect(Collectors.toSet()));

        // Three RESERVE, One CREATE, three RESERVE (for executor) and two LAUNCH_GROUP operations
        Assert.assertEquals(Arrays.asList(
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.CREATE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                Protos.Offer.Operation.Type.LAUNCH_GROUP),
                response.recommendations.stream().map(r -> r.getOperation().getType()).collect(Collectors.toList()));
    }

    @Test
    public void testConfigurationUpdate() throws Exception {
        // Get first Step associated with Task A-0
        Plan plan = getDeploymentPlan();
        Step stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertTrue(stepTaskA0.isPending());

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(true), defaultScheduler.getClientStatus());

        // Offer sufficient Resource and wait for its acceptance
        Protos.Offer offer1 = getSufficientOfferForTaskA();
        OfferResponse response = defaultScheduler.offers(Arrays.asList(offer1));
        Assert.assertEquals(8, response.recommendations.size());
        Assert.assertEquals(offer1, response.recommendations.iterator().next().getOffer());
        Protos.TaskID launchedTaskId = getTaskId(response.recommendations);

        // Send TASK_RUNNING status after the task is Starting (Mesos has been sent Launch)
        Assert.assertTrue(stepTaskA0.isStarting());
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Assert.assertTrue(stepTaskA0.isComplete());
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING),
                getStepStatuses(plan));

        Assert.assertTrue(stepTaskA0.isComplete());
        Assert.assertEquals(0, getRecoveryPlan().getChildren().size());

        // Perform Configuration Update
        Capabilities.overrideCapabilities(getCapabilities());
        defaultScheduler = getScheduler(getServiceSpec(updatedPodA, podB));
        plan = getDeploymentPlan();
        stepTaskA0 = plan.getChildren().get(0).getChildren().get(0);
        Assert.assertEquals(Status.PENDING, stepTaskA0.getStatus());

        List<Protos.Resource> expectedResources = new ArrayList<>(getExpectedResources(response.recommendations));
        Protos.Resource neededAdditionalResource = ResourceTestUtils.getUnreservedCpus(UPDATED_TASK_A_CPU - TASK_A_CPU);
        expectedResources.add(neededAdditionalResource);

        // Start update Step: check behavior before and after reconciliation completes
        Protos.Offer insufficientOffer = OfferTestUtils.getCompleteOffer(neededAdditionalResource);

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(true), defaultScheduler.getClientStatus());

        // First attempt doesn't do anything because reconciliation hadn't completed yet
        Collection<Protos.Offer> offers = Arrays.asList(insufficientOffer);
        response = defaultScheduler.offers(offers);
        Assert.assertEquals(OfferResponse.Result.NOT_READY, response.result);
        Assert.assertEquals(Arrays.asList(insufficientOffer),
                OfferUtils.filterOutAcceptedOffers(offers, response.recommendations));
        verify(mockSchedulerDriver, times(0)).killTask(any());
        Assert.assertEquals(Status.PENDING, stepTaskA0.getStatus());

        // Check that the scheduler had requested reconciliation of its sole task, then finish that reconciliation:
        verify(mockSchedulerDriver, times(1)).reconcileTasks(
                Arrays.asList(getTaskStatus(launchedTaskId, Protos.TaskState.TASK_RUNNING)));
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(false), defaultScheduler.getClientStatus());

        // Second attempt after reconciliation results in triggering task relaunch
        response = defaultScheduler.offers(offers);
        Assert.assertEquals(OfferResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(Arrays.asList(insufficientOffer),
                OfferUtils.filterOutAcceptedOffers(offers, response.recommendations));
        verify(mockSchedulerDriver, times(1)).killTask(launchedTaskId);
        Assert.assertEquals(Status.PREPARED, stepTaskA0.getStatus());

        // Sent TASK_KILLED status
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_KILLED);
        Assert.assertEquals(Status.PREPARED, stepTaskA0.getStatus());
        Assert.assertEquals(0, getRecoveryPlan().getChildren().size());

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(false), defaultScheduler.getClientStatus());

        Protos.Offer expectedOffer = OfferTestUtils.getCompleteOffer(expectedResources);
        response = defaultScheduler.offers(Arrays.asList(expectedOffer));
        Assert.assertEquals(OfferResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(Arrays.asList(Protos.Offer.Operation.Type.RESERVE, Protos.Offer.Operation.Type.LAUNCH_GROUP),
                response.recommendations.stream().map(r -> r.getOperation().getType()).collect(Collectors.toList()));
        Assert.assertEquals(expectedOffer, response.recommendations.iterator().next().getOffer());
        Assert.assertTrue(stepTaskA0.isStarting());
        Assert.assertEquals(0, getRecoveryPlan().getChildren().size());

        launchedTaskId = getTaskId(response.recommendations);
        // Send TASK_RUNNING status after the task is Starting (Mesos has been sent Launch)
        Assert.assertTrue(stepTaskA0.isStarting());
        statusUpdate(launchedTaskId, Protos.TaskState.TASK_RUNNING);
        Assert.assertTrue(stepTaskA0.isComplete());
    }

    @Test
    public void testInvalidConfigurationUpdate() throws Exception {
        // Launch A and B in original configuration
        testLaunchB();

        // Get initial target config UUID
        UUID targetConfigId = defaultScheduler.getConfigStore().getTargetConfig();

        // Build new scheduler with invalid config (shrinking task count)
        Capabilities.overrideCapabilities(getCapabilities());
        defaultScheduler = getScheduler(getServiceSpec(podA, invalidPodB));

        // Ensure prior target configuration is still intact
        Assert.assertEquals(targetConfigId, defaultScheduler.getConfigStore().getTargetConfig());

        Assert.assertEquals(1, getDeploymentPlan().getErrors().size());
        Assert.assertTrue(getDeploymentPlan().getErrors().get(0).contains("Transition: '2' => '1'"));
    }

    private static List<Protos.Resource> getExpectedResources(Collection<OfferRecommendation> operations) {
        for (OfferRecommendation operation : operations) {
            if (operation.getOperation().getType().equals(Offer.Operation.Type.LAUNCH_GROUP)) {
                return Stream.concat(
                                operation.getOperation().getLaunchGroup().getTaskGroup().getTasksList().stream()
                                    .flatMap(taskInfo -> taskInfo.getResourcesList().stream()),
                                operation.getOperation().getLaunchGroup().getExecutor().getResourcesList().stream())
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    @Test
    public void testTaskIpIsStoredOnInstall() throws Exception {
        install();

        // Verify the TaskIP (TaskInfo, strictly speaking) has been stored in the Persister.
        StateStore stateStore = new StateStore(persister);
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME).isPresent());
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_B_POD_NAME + "-0-" + TASK_B_NAME).isPresent());
    }

    @Test
    public void testTaskIpIsUpdatedOnStatusUpdate() throws Exception {
        List<Protos.TaskID> taskIds = install();
        String updateIp = "1.1.1.1";

        // Verify the TaskIP (TaskInfo, strictly speaking) has been stored in the Persister.
        StateStore stateStore = new StateStore(persister);
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME).isPresent());
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_B_POD_NAME + "-0-" + TASK_B_NAME).isPresent());

        Protos.TaskStatus update = Protos.TaskStatus.newBuilder(
                getTaskStatus(taskIds.get(0), Protos.TaskState.TASK_STAGING))
                .setContainerStatus(Protos.ContainerStatus.newBuilder()
                        .addNetworkInfos(Protos.NetworkInfo.newBuilder()
                            .addIpAddresses(Protos.NetworkInfo.IPAddress.newBuilder()
                                .setIpAddress(updateIp))))
                .build();
        defaultScheduler.taskStatus(update);

        // Verify the TaskStatus was updated.
        Assert.assertEquals(updateIp,
                StateStoreUtils.getTaskStatusFromProperty(
                        stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME).get()
                                .getContainerStatus().getNetworkInfos(0).getIpAddresses(0).getIpAddress());
    }

    @Test
    public void testTaskIpIsNotOverwrittenByEmptyOnUpdate() throws Exception {
        List<Protos.TaskID> taskIds = install();

        // Verify the TaskIP (TaskInfo, strictly speaking) has been stored in the Persister.
        StateStore stateStore = new StateStore(persister);
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME).isPresent());
        Assert.assertTrue(StateStoreUtils.getTaskStatusFromProperty(
                stateStore, TASK_B_POD_NAME + "-0-" + TASK_B_NAME).isPresent());

        Protos.TaskStatus update = Protos.TaskStatus.newBuilder(
                getTaskStatus(taskIds.get(0), Protos.TaskState.TASK_STAGING))
                .setContainerStatus(Protos.ContainerStatus.newBuilder()
                        .addNetworkInfos(Protos.NetworkInfo.newBuilder()))
                .build();
        defaultScheduler.taskStatus(update);

        // Verify the TaskStatus was NOT updated.
        Assert.assertEquals(TASK_IP,
                StateStoreUtils.getTaskStatusFromProperty(
                        stateStore, TASK_A_POD_NAME + "-0-" + TASK_A_NAME).get()
                                .getContainerStatus().getNetworkInfos(0).getIpAddresses(0).getIpAddress());
    }

    @Test
    public void testFinishedService() throws Exception {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(mockSchedulerDriver);

        when(mockSchedulerConfig.isStateCacheEnabled()).thenReturn(true);
        ServiceSpec serviceSpec = DefaultServiceSpec.newBuilder(getServiceSpec(podA, podB))
                .goalState(GoalState.FINISH)
                .build();
        defaultScheduler = getScheduler(serviceSpec);

        Assert.assertFalse(getDeploymentPlan().isComplete());
        Assert.assertTrue(getRecoveryPlan().isComplete());

        // Kick getClientStatus() before calling offers():
        Assert.assertEquals(ClientStatusResponse.footprint(true), defaultScheduler.getClientStatus());

        // Deployment hasn't finished, so service isn't FINISHED:
        OfferResponse offerResponse = defaultScheduler.offers(Collections.emptyList());
        Assert.assertEquals(OfferResponse.Result.PROCESSED, offerResponse.result);
        Assert.assertTrue(offerResponse.recommendations.isEmpty());

        // Finish the deployment. First step is now PREPARED due to the offer we provided above:
        Protos.TaskID taskId = installStep(0, 0, getSufficientOfferForTaskA(), Status.PREPARED, false);
        installStep(1, 0, getSufficientOfferForTaskB(), Status.PENDING, true);

        // Still RESERVING before the last step is completed:
        installStep(1, 1, getSufficientOfferForTaskB(), Status.PENDING, true);

        Assert.assertTrue(getDeploymentPlan().isComplete());
        Assert.assertTrue(getRecoveryPlan().isComplete());

        // Now that Deployment has finished, service is FINISHED:
        Assert.assertEquals(ClientStatusResponse.readyToUninstall(), defaultScheduler.getClientStatus());

        // Force recovery action, at which point scheduler should go back to RUNNING
        // (verify recovery is being monitored):
        statusUpdate(taskId, Protos.TaskState.TASK_FAILED);

        // Implementation detail: Now that the recovery action is pending, the scheduler should have reverted to a
        // launching state so that it starts getting offers again. In practice, though, by this point it would already
        // be switched over to an UninstallScheduler at this point.
        Assert.assertEquals(ClientStatusResponse.launching(true), defaultScheduler.getClientStatus());
        Assert.assertTrue(getDeploymentPlan().isComplete());
        Assert.assertFalse(getRecoveryPlan().isComplete());
        Assert.assertEquals(Arrays.asList(Status.PENDING), getStepStatuses(getRecoveryPlan()));

        Assert.assertEquals(OfferResponse.Result.PROCESSED, defaultScheduler.offers(Collections.emptyList()).result);

        // After giving offers a kick, we're running again:
        Assert.assertEquals(ClientStatusResponse.launching(false), defaultScheduler.getClientStatus());
        Assert.assertTrue(getDeploymentPlan().isComplete());
        Assert.assertFalse(getRecoveryPlan().isComplete());
    }

    @Test
    public void testDecommissionPlanCustomization() throws Exception {
        AtomicBoolean decommissionPlanCustomized = new AtomicBoolean(false);
        PlanCustomizer planCustomizer = new PlanCustomizer() {
            @Override
            public Plan updatePlan(Plan plan) {
                if (plan.isDecommissionPlan()) {
                    decommissionPlanCustomized.set(true);
                }

                return plan;
            }
        };

        // Launches the first instance of POD-B
        testLaunchB();

        // Launch the second instance of POD-B
        installStep(1, 1, getSufficientOfferForTaskB(), Status.PENDING, true);
        Assert.assertEquals(ClientStatusResponse.idle(), defaultScheduler.getClientStatus());
        Assert.assertEquals(
                Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE),
                getStepStatuses(getDeploymentPlan()));

        // Construct POD-B which scales in by 1 pod instance
        PodSpec scaledInPodB = DefaultPodSpec.newBuilder(podB)
                .count(TASK_B_COUNT - 1)
                .allowDecommission(true)
                .build();

        DefaultScheduler.newBuilder(
                getServiceSpec(podA, scaledInPodB), SchedulerConfigTestUtils.getTestSchedulerConfig(), persister)
                .setPlanCustomizer(planCustomizer)
                .build();

        Assert.assertTrue(decommissionPlanCustomized.get());
    }

    @Test
    public void testUnexpectedPermanentlyFailedResources() throws Exception {
        install();

        StateStore stateStore = new StateStore(persister);
        // Pick an arbitrary task with resources:
        Protos.TaskInfo taskInfo = stateStore.fetchTasks().iterator().next();
        Assert.assertFalse(taskInfo.getResourcesList().isEmpty());

        // Verify that the task's resources are currently expected:
        UnexpectedResourcesResponse response =
                defaultScheduler.getUnexpectedResources(OfferTestUtils.getOffers(taskInfo.getResourcesList()));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertTrue(response.offerResources.isEmpty());

        // Mark the task as permanently failed:
        stateStore.storeTasks(Collections.singletonList(
                taskInfo.toBuilder()
                        .setLabels(new TaskLabelWriter(taskInfo).setPermanentlyFailed().toProto())
                        .build()));

        // Verify that the task's resources are no longer expected:
        response = defaultScheduler.getUnexpectedResources(OfferTestUtils.getOffers(taskInfo.getResourcesList()));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(1, response.offerResources.size());
        Assert.assertEquals(taskInfo.getResourcesList(), response.offerResources.iterator().next().getResources());
    }

    @Test
    public void testUnexpectedDecommissioningResources() throws Exception {
        install();

        StateStore stateStore = new StateStore(persister);
        // Pick an arbitrary task with resources:
        Protos.TaskInfo taskInfo = stateStore.fetchTasks().iterator().next();
        Assert.assertFalse(taskInfo.getResourcesList().isEmpty());

        // Verify that the task's resources are currently expected:
        UnexpectedResourcesResponse response =
                defaultScheduler.getUnexpectedResources(OfferTestUtils.getOffers(taskInfo.getResourcesList()));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertTrue(response.offerResources.isEmpty());

        // Mark the task as decommissioning:
        stateStore.storeGoalOverrideStatus(taskInfo.getName(), DecommissionPlanFactory.DECOMMISSIONING_STATUS);

        // Verify that the task's resources are no longer expected:
        response = defaultScheduler.getUnexpectedResources(OfferTestUtils.getOffers(taskInfo.getResourcesList()));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(1, response.offerResources.size());
        Assert.assertEquals(taskInfo.getResourcesList(), response.offerResources.iterator().next().getResources());

        // Note: The task's resources are still present in the state store, because we didn't set up a decommission plan
    }

    @Test
    public void testLaunchTransient() throws Exception {
        Protos.Resource resource = ResourceTestUtils.getUnreservedCpus(3);
        Offer offer = OfferTestUtils.getCompleteOffer(resource);
        Protos.TaskInfo.Builder taskInfoBuilder = TaskTestUtils.getTaskInfo(resource).toBuilder();

        OfferRecommendation recommendationToLaunch =
                new LaunchOfferRecommendation(
                        offer,
                        taskInfoBuilder.build(),
                        Protos.ExecutorInfo.newBuilder().setExecutorId(TestConstants.EXECUTOR_ID).build(),
                        true);
        List<OfferRecommendation> allRecommendations = Arrays.asList(
                new LaunchOfferRecommendation(
                        offer,
                        taskInfoBuilder.build(),
                        Protos.ExecutorInfo.newBuilder().setExecutorId(TestConstants.EXECUTOR_ID).build(),
                        false),
                recommendationToLaunch,
                new LaunchOfferRecommendation(
                        offer,
                        taskInfoBuilder.build(),
                        Protos.ExecutorInfo.newBuilder().setExecutorId(TestConstants.EXECUTOR_ID).build(),
                        false));

        PlanScheduler mockPlanScheduler = mock(PlanScheduler.class);
        when(mockPlanScheduler.resourceOffers(any(), any())).thenReturn(allRecommendations);

        PersistentLaunchRecorder mockLaunchRecorder = mock(PersistentLaunchRecorder.class);
        UninstallRecorder mockDecommissionRecorder = mock(UninstallRecorder.class);

        Collection<OfferRecommendation> recommendations = DefaultScheduler.processOffers(
                LoggingUtils.getLogger(getClass()),
                mockPlanScheduler,
                mockLaunchRecorder,
                Optional.of(mockDecommissionRecorder),
                Collections.emptyList(),
                Collections.emptyList()).recommendations;
        // Only recommendationToLaunch should have been returned. The others should be filtered due to !shouldLaunch():
        Assert.assertEquals(1, recommendations.size());
        Assert.assertEquals(recommendationToLaunch, recommendations.iterator().next());

        // Meanwhile, ALL of the recommendations (including the two with launch=false) should have been passed to the recorders:
        verify(mockLaunchRecorder).record(allRecommendations);
        verify(mockDecommissionRecorder).recordRecommendations(allRecommendations);
    }

    private static int countOperationType(
            Protos.Offer.Operation.Type operationType, Collection<OfferRecommendation> operations) {
        int count = 0;
        for (OfferRecommendation operation : operations) {
            if (operation.getOperation().getType().equals(operationType)) {
                count++;
            }
        }
        return count;
    }

    private static Protos.TaskInfo getTask(Collection<OfferRecommendation> operations) {
        for (OfferRecommendation operation : operations) {
            if (operation.getOperation().getType().equals(Offer.Operation.Type.LAUNCH_GROUP)) {
                return operation.getOperation().getLaunchGroup().getTaskGroup().getTasks(0);
            }
        }

        return null;
    }

    private static Protos.TaskID getTaskId(Collection<OfferRecommendation> operations) {
        return getTask(operations).getTaskId();
    }

    private static Protos.TaskStatus getTaskStatus(Protos.TaskID taskID, Protos.TaskState state) {
        Protos.TaskStatus.Builder builder = Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(state);
        builder.getContainerStatusBuilder().addNetworkInfosBuilder().addIpAddressesBuilder().setIpAddress(TASK_IP);
        return builder.build();
    }

    private static Protos.Offer getInsufficientOfferForTaskA(UUID offerId) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(offerId.toString()).build())
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpus(TASK_A_CPU / 2.0),
                                ResourceTestUtils.getUnreservedMem(TASK_A_MEM / 2.0)))
                .build();
    }

    private static Protos.Offer getSufficientOfferForTaskA() {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(UUID.randomUUID().toString()).build())
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpus(TASK_A_CPU + 0.1),
                                ResourceTestUtils.getUnreservedMem(TASK_A_MEM + 32),
                                ResourceTestUtils.getUnreservedDisk(TASK_A_DISK + 256)))
                .build();
    }

    private static Protos.Offer getSufficientOfferForTaskB() {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(UUID.randomUUID().toString()).build())
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .addAllResources(
                        Arrays.asList(
                                ResourceTestUtils.getUnreservedCpus(TASK_B_CPU + 0.1),
                                ResourceTestUtils.getUnreservedMem(TASK_B_MEM + 32),
                                ResourceTestUtils.getUnreservedDisk(TASK_B_DISK + 256)))
                .build();
    }

    private Protos.TaskID installStep(
            int phaseIndex, int stepIndex, Protos.Offer offer, Status expectedStepStatus, boolean hasNewWork)
            throws Exception {

        // Kick getClientStatus() in preparation for the following offers() call
        Assert.assertEquals(ClientStatusResponse.footprint(hasNewWork), defaultScheduler.getClientStatus());

        // After updating plan state, get first Step associated with Task A-0
        Step step = getDeploymentPlan().getChildren().get(phaseIndex).getChildren().get(stepIndex);
        Assert.assertEquals(expectedStepStatus, step.getStatus());

        // Offer sufficient Resource and wait for its acceptance
        OfferResponse response = defaultScheduler.offers(Collections.singletonList(offer));
        for (OfferRecommendation rec : response.recommendations) {
            Assert.assertEquals(offer, rec.getOffer());
        }

        // Verify operations:
        Assert.assertEquals(8, response.recommendations.size());
        Assert.assertEquals(6, countOperationType(Protos.Offer.Operation.Type.RESERVE, response.recommendations));
        Assert.assertEquals(1, countOperationType(Protos.Offer.Operation.Type.CREATE, response.recommendations));
        Assert.assertEquals(1, countOperationType(Offer.Operation.Type.LAUNCH_GROUP, response.recommendations));
        Assert.assertTrue(step.isStarting());

        // Sent TASK_RUNNING status
        Protos.TaskID taskId = getTaskId(response.recommendations);
        statusUpdate(taskId, Protos.TaskState.TASK_RUNNING);

        // Wait for the Step to become Complete
        Assert.assertTrue(step.isComplete());

        return taskId;
    }

    private void statusUpdate(Protos.TaskID launchedTaskId, Protos.TaskState state) {
        Protos.TaskStatus runningStatus = getTaskStatus(launchedTaskId, state);
        defaultScheduler.taskStatus(runningStatus);
    }

    /**
     * Installs the service.
     */
    private List<Protos.TaskID> install() throws Exception {
        List<Protos.TaskID> taskIds = new ArrayList<>();

        taskIds.add(installStep(0, 0, getSufficientOfferForTaskA(), Status.PENDING, true));
        taskIds.add(installStep(1, 0, getSufficientOfferForTaskB(), Status.PENDING, true));
        taskIds.add(installStep(1, 1, getSufficientOfferForTaskB(), Status.PENDING, true));

        Assert.assertTrue(getDeploymentPlan().isComplete());
        Assert.assertEquals(Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE),
                getStepStatuses(getDeploymentPlan()));
        Assert.assertTrue(defaultScheduler.getPlanCoordinator().getCandidates().isEmpty());

        return taskIds;
    }

    private DefaultScheduler getScheduler(ServiceSpec serviceSpec) throws PersisterException {
        AbstractScheduler scheduler = DefaultScheduler.newBuilder(
                serviceSpec, SchedulerConfigTestUtils.getTestSchedulerConfig(), persister)
                .build();
        scheduler.registered(false);
        return (DefaultScheduler) scheduler;
    }

    private Plan getDeploymentPlan() throws Exception {
        return getPlan(defaultScheduler.getPlanCoordinator(), Constants.DEPLOY_PLAN_NAME);
    }

    private Plan getRecoveryPlan() throws Exception {
        return getPlan(defaultScheduler.getPlanCoordinator(), Constants.RECOVERY_PLAN_NAME);
    }

    private static Plan getPlan(PlanCoordinator planCoordinator, String planName) throws Exception {
        return planCoordinator.getPlanManagers().stream()
                .filter(pm -> pm.getPlan().getName().equals(planName))
                .findAny()
                .get()
                .getPlan();
    }

    private static List<Status> getStepStatuses(Plan plan) {
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .map(Element::getStatus)
                .collect(Collectors.toList());
    }
}
