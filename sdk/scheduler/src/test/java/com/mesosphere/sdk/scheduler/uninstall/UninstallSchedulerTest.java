package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.Driver;
import com.mesosphere.sdk.scheduler.framework.MesosEventClient.OfferResponse;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.*;

public class UninstallSchedulerTest extends DefaultCapabilitiesTestSuite {

    private static final String RESERVED_RESOURCE_1_ID = "reserved-resource-id";
    private static final String RESERVED_RESOURCE_2_ID = "reserved-volume-id";
    private static final String RESERVED_RESOURCE_3_ID = "reserved-cpu-id-0";
    private static final String RESERVED_RESOURCE_4_ID = "reserved-cpu-id-1";

    private static final Protos.Resource RESERVED_RESOURCE_1 =
            ResourceTestUtils.getReservedPorts(123, 234, RESERVED_RESOURCE_1_ID);
    private static final Protos.Resource RESERVED_RESOURCE_2 =
            ResourceTestUtils.getReservedRootVolume(999.0, RESERVED_RESOURCE_2_ID, RESERVED_RESOURCE_2_ID);
    private static final Protos.Resource RESERVED_RESOURCE_3 =
            ResourceTestUtils.getReservedCpus(1.0, RESERVED_RESOURCE_3_ID);
    private static final Protos.Resource RESERVED_RESOURCE_4 =
            ResourceTestUtils.getReservedCpus(1.0, RESERVED_RESOURCE_4_ID);

    private static final Protos.TaskInfo TASK_A =
            TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_1, RESERVED_RESOURCE_2, RESERVED_RESOURCE_3));
    private static final Protos.TaskInfo TASK_B;
    static {
        // Mark this one as permanently failed. Doesn't take effect unless the task is ALSO in an error state:
        Protos.TaskInfo.Builder builder = Protos.TaskInfo.newBuilder(
                TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_2, RESERVED_RESOURCE_4)))
                .setTaskId(CommonIdUtils.toTaskId("other-task-info"))
                .setName("other-task-info");
        builder.setLabels(new TaskLabelWriter(builder)
                .setPermanentlyFailed()
                .toProto());
        TASK_B = builder.build();
    }
    private static final Protos.TaskStatus TASK_B_STATUS_ERROR =
            TaskTestUtils.generateStatus(TASK_B.getTaskId(), Protos.TaskState.TASK_ERROR);

    private FrameworkStore frameworkStore;
    private StateStore stateStore;

    @Mock private ConfigStore<ServiceSpec> mockConfigStore;
    @Mock private SchedulerDriver mockSchedulerDriver;
    @Mock private SecretsClient mockSecretsClient;
    @Mock private PlanCustomizer mockPlanCustomizer;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(mockSchedulerDriver);

        Persister persister = new MemPersister();
        frameworkStore = new FrameworkStore(persister);
        frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
        stateStore = new StateStore(persister);
        stateStore.storeTasks(Collections.singletonList(TASK_A));

        // Have the mock plan customizer default to returning the plan unchanged.
        when(mockPlanCustomizer.updateUninstallPlan(any())).thenAnswer(invocation -> invocation.getArguments()[0]);
    }

    @Test
    public void testEmptyDeployPlan() {
        // Sanity check...
        Assert.assertEquals(Constants.DEPLOY_PLAN_NAME, UninstallScheduler.EMPTY_DEPLOY_PLAN.getName());
        Assert.assertEquals(Status.COMPLETE, UninstallScheduler.EMPTY_DEPLOY_PLAN.getStatus());
        Assert.assertTrue(UninstallScheduler.EMPTY_DEPLOY_PLAN.getChildren().isEmpty());
    }

    @Test
    public void testEmptyOffers() throws Exception {
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        Assert.assertEquals(OfferResponse.Result.PROCESSED, uninstallScheduler.offers(Collections.emptyList()).result);
        verify(mockSchedulerDriver, times(1)).reconcileTasks(any());
        verify(mockSchedulerDriver, times(0)).acceptOffers(any(), anyCollectionOf(Protos.Offer.Operation.class), any());
        verify(mockSchedulerDriver, times(0)).declineOffer(any(), any());
    }

    @Test
    public void testInitialPlan() throws Exception {
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        // 1 task kill + 3 unique resources + deregister step
        List<Status> expected = Arrays.asList(Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testInitialPlanTaskResourceOverlap() throws Exception {
        // Add TASK_B, which overlaps partially with TASK_A.
        stateStore.storeTasks(Collections.singletonList(TASK_B));

        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        // 2 task kills + 4 unique resources + deregister step.
        List<Status> expected = Arrays.asList(
                Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING,
                Status.PENDING, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testInitialPlanTaskError() throws Exception {
        // Specify TASK_ERROR status for TASK_B. Its sole exclusive resource should then be omitted from the plan:
        stateStore.storeTasks(Collections.singletonList(TASK_B));
        stateStore.storeStatus(TASK_B.getName(), TASK_B_STATUS_ERROR);

        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        // 2 task kills + 3 unique resources (from task A, not task B) + deregister step.
        List<Status> expected = Arrays.asList(
                Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testUninstallStepsPrepared() throws Exception {
        // Initial call to resourceOffers() will return all steps from resource phase as candidates
        // regardless of the offers sent in, and will start the steps.
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        uninstallScheduler.offers(Collections.singletonList(getOffer()));
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        // 1 task kill + 3 resources + deregister step.
        List<Status> expected = Arrays.asList(Status.COMPLETE, Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        // Another offer cycle should get the resources pending
        uninstallScheduler.offers(Collections.singletonList(getOffer()));
        // 1 task kill + 3 resources + deregister step.
        expected = Arrays.asList(Status.COMPLETE, Status.PREPARED, Status.PREPARED, Status.PREPARED, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testUninstallStepsComplete() throws Exception {
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(RESERVED_RESOURCE_1, RESERVED_RESOURCE_2));
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        uninstallScheduler.offers(Collections.singletonList(offer));
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        List<Status> expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        offer = OfferTestUtils.getOffer(Collections.singletonList(RESERVED_RESOURCE_3));
        uninstallScheduler.offers(Collections.singletonList(offer));
        expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING);
        Assert.assertEquals(expected, getStepStatuses(plan));
    }

    @Test
    public void testPlanCompletes() throws Exception {
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(
                RESERVED_RESOURCE_1, RESERVED_RESOURCE_2, RESERVED_RESOURCE_3));
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        uninstallScheduler.offers(Collections.singletonList(offer));
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        List<Status> expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        // Turn the crank once to finish the last Step
        uninstallScheduler.offers(Arrays.asList(getOffer()));
        plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
        Assert.assertTrue(plan.isComplete());
    }

    @Test
    public void testAllButDeregisteredPlanCompletes() throws Exception {
        // New empty state store: No framework ID is set yet, and there are no tasks, and no SchedulerDriver
        Persister persister = new MemPersister();
        UninstallScheduler uninstallScheduler = new UninstallScheduler(
                getServiceSpec(),
                new FrameworkStore(persister),
                new StateStore(persister),
                mockConfigStore,
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Optional.empty(), Optional.of(mockSecretsClient));
        // Returns a simple placeholder plan with status COMPLETE
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();
        Assert.assertTrue(plan.toString(), plan.isComplete());
        Assert.assertTrue(plan.getChildren().isEmpty());
        // Doesn't want to register with Mesos:
        Assert.assertFalse(uninstallScheduler.shouldRegisterFramework());
    }

    @Test
    public void testTLSCleanupInvoked() throws Exception {
        // Populate ServiceSpec with a task containing a TransportEncryptionSpec:
        ServiceSpec serviceSpecWithTLSTasks = getServiceSpec();
        TaskSpec mockTask = mock(TaskSpec.class);
        when(mockTask.getTransportEncryption()).thenReturn(Arrays.asList(
                new DefaultTransportEncryptionSpec("foo", TransportEncryptionSpec.Type.KEYSTORE)));
        PodSpec mockPod = mock(PodSpec.class);
        when(mockPod.getTasks()).thenReturn(Arrays.asList(mockTask));
        when(serviceSpecWithTLSTasks.getPods()).thenReturn(Arrays.asList(mockPod));

        UninstallScheduler uninstallScheduler = getUninstallScheduler(serviceSpecWithTLSTasks);
        PlanCoordinator planCoordinator = uninstallScheduler.getPlanCoordinator();
        Plan plan = planCoordinator.getPlanManagers().stream().findFirst().get().getPlan();

        when(mockSecretsClient.list(TestConstants.SERVICE_NAME)).thenReturn(Collections.emptyList());

        // Run through the task cleanup phase
        uninstallScheduler.register(false); // TODO REMOVE??
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(
                RESERVED_RESOURCE_1, RESERVED_RESOURCE_2, RESERVED_RESOURCE_3));
        uninstallScheduler.offers(Collections.singletonList(offer));
        List<Status> expected = Arrays.asList(
                Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        // Then the TLS cleanup phase
        uninstallScheduler.offers(Collections.singletonList(getOffer()));
        expected = Arrays.asList(
                Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        verify(mockSecretsClient, times(1)).list(TestConstants.SERVICE_NAME);

        // Then the final Deregister phase
        uninstallScheduler.offers(Collections.singletonList(getOffer()));
        expected = Arrays.asList(
                Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        Assert.assertTrue(plan.isComplete());
    }

    @Test
    public void testUninstallPlanCustomizer() throws Exception {
        UninstallScheduler uninstallScheduler = new UninstallScheduler(
                getServiceSpec(),
                frameworkStore,
                stateStore,
                mockConfigStore,
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Optional.of(getReversingPlanCustomizer()),
                Optional.of(mockSecretsClient));
        uninstallScheduler.start();

        Plan plan = uninstallScheduler.getPlanCoordinator().getPlanManagers().stream().findFirst().get().getPlan();

        // The standard order is kill-tasks, unreserve-resources, deregister-service. Verify the inverse
        // is now true.
        Assert.assertEquals("deregister-service", plan.getChildren().get(0).getName());
        Assert.assertEquals("unreserve-resources", plan.getChildren().get(1).getName());
        Assert.assertEquals("kill-tasks", plan.getChildren().get(2).getName());
    }

    private static Protos.Offer getOffer() {
        return getOffer(UUID.randomUUID().toString());
    }

    private static Protos.Offer getOffer(String id) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(id))
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .build();
    }

    private UninstallScheduler getUninstallScheduler() {
        return getUninstallScheduler(getServiceSpec());
    }

    private UninstallScheduler getUninstallScheduler(ServiceSpec serviceSpec) {
        UninstallScheduler uninstallScheduler = new UninstallScheduler(
                serviceSpec,
                frameworkStore,
                stateStore,
                mockConfigStore,
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Optional.of(mockPlanCustomizer),
                Optional.of(mockSecretsClient));
        uninstallScheduler
                .start()
                .register(false);
        return uninstallScheduler;
    }

    private static ServiceSpec getServiceSpec() {
        ServiceSpec mockServiceSpec = mock(ServiceSpec.class);
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        return mockServiceSpec;
    }

    private static List<Status> getStepStatuses(Plan plan) {
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .map(Element::getStatus)
                .collect(Collectors.toList());
    }

    private static PlanCustomizer getReversingPlanCustomizer() {
        return (new PlanCustomizer() {
            @Override
            public Plan updatePlan(Plan plan) {
                return plan;
            }

            @Override
            public Plan updateUninstallPlan(Plan uninstallPlan) {
                Collections.reverse(uninstallPlan.getChildren());

                return uninstallPlan;
            }
        });
    }
}
