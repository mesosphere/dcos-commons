package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.framework.Driver;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.MesosEventClient.OfferResponse;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.scheduler.MesosEventClient.UnexpectedResourcesResponse;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
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

    private static final String RESERVED_RESOURCE_1_ID = "resource-1";
    private static final String RESERVED_RESOURCE_2_ID = "resource-2";
    private static final String RESERVED_RESOURCE_3_ID = "resource-3";
    private static final String RESERVED_RESOURCE_4_ID = "resource-4";

    private static final Protos.Resource RESERVED_RESOURCE_1 =
            ResourceTestUtils.getReservedPorts(123, 234, RESERVED_RESOURCE_1_ID);
    private static final Protos.Resource RESERVED_RESOURCE_2 =
            ResourceTestUtils.getReservedRootVolume(999.0, RESERVED_RESOURCE_2_ID, RESERVED_RESOURCE_2_ID);
    private static final Protos.Resource RESERVED_RESOURCE_3 =
            ResourceTestUtils.getReservedCpus(1.0, RESERVED_RESOURCE_3_ID);
    private static final Protos.Resource RESERVED_RESOURCE_4 =
            ResourceTestUtils.getReservedCpus(1.0, RESERVED_RESOURCE_4_ID);

    private static final Protos.TaskInfo TASK_A;
    private static final Protos.TaskInfo TASK_B;
    private static final Protos.TaskInfo TASK_C;
    static {
        Protos.TaskInfo.Builder builder = Protos.TaskInfo.newBuilder(
                TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_1, RESERVED_RESOURCE_2, RESERVED_RESOURCE_3)));
        builder.setLabels(new TaskLabelWriter(builder)
                .setHostname(OfferTestUtils.getEmptyOfferBuilder().setHostname("host-1").build())
                .toProto());
        TASK_A = builder.build();

        // - Marked as permanently failed: Filtered from resources to clean up if the TaskStatus is ALSO ERROR.
        // - No agent id: put into a default 'UNKNOWN_AGENT' phase.
        // - Shares resources with TASK_A, but they are not deduped due to the different agent.
        builder = Protos.TaskInfo.newBuilder(
                TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_2, RESERVED_RESOURCE_4)))
                .setTaskId(CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, "task-b"))
                .setName("task-b");
        builder.setLabels(new TaskLabelWriter(builder)
                .setPermanentlyFailed()
                .toProto());
        TASK_B = builder.build();

        // - Marked as permanently failed: Filtered from resources to clean up if the TaskStatus is ALSO ERROR.
        // - Same agent as TASK_A.
        // - Shares resources with TASK_A, which are deduped because they share the same agent.
        builder = Protos.TaskInfo.newBuilder(
                TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_1, RESERVED_RESOURCE_4)))
                .setTaskId(CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, "task-c"))
                .setName("task-c");
        builder.setLabels(new TaskLabelWriter(builder)
                .setHostname(OfferTestUtils.getEmptyOfferBuilder().setHostname("host-1").build())
                .setPermanentlyFailed()
                .toProto());
        TASK_C = builder.build();
    }

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
        stateStore = new StateStore(persister);
        stateStore.storeTasks(Collections.singletonList(TASK_A));

        // Have the mock plan customizer default to returning the plan unchanged.
        when(mockPlanCustomizer.updateUninstallPlan(any())).thenAnswer(invocation -> invocation.getArguments()[0]);
    }

    @Test
    public void testEmptyOffers() throws Exception {
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());
        Assert.assertEquals(OfferResponse.Result.PROCESSED, uninstallScheduler.offers(Collections.emptyList()).result);
        verify(mockSchedulerDriver, times(0)).acceptOffers(any(), anyCollectionOf(Protos.Offer.Operation.class), any());
        verify(mockSchedulerDriver, times(0)).declineOffer(any(), any());
    }

    @Test
    public void testInitialPlan() throws Exception {
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        Plan plan = getUninstallPlan(uninstallScheduler);
        // 1 task kill + 3 unique resources + deregister step
        List<Status> expected = Arrays.asList(Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testInitialPlanTaskResourceOverlap() throws Exception {
        // Add TASK_B and TASK_C:
        // - TASK_B overlaps partially with TASK_A, but is on a different agent so resources aren't deduped.
        // - TASK_C meanwhile is on the same agent as TASK_A, so its resources ARE deduped.
        stateStore.storeTasks(Arrays.asList(TASK_B, TASK_C));

        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        Plan plan = getUninstallPlan(uninstallScheduler);
        List<Status> expected = Arrays.asList(
                Status.PENDING, Status.PENDING, Status.PENDING, // 3 task kills
                Status.PENDING, Status.PENDING, // 2 resources on UNKNOWN_AGENT (TASK_B)
                Status.PENDING, Status.PENDING, Status.PENDING, Status.PENDING, // 4 deduped resources on host-1 (TASK_A + TASK_C)
                Status.PENDING); // deregister step
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testInitialPlanTaskError() throws Exception {
        // Specify TASK_ERROR status for TASK_B and TASK_C. Their exclusive resources should then be omitted from the plan:
        stateStore.storeTasks(Arrays.asList(TASK_B, TASK_C));
        stateStore.storeStatus(TASK_B.getName(), TaskTestUtils.generateStatus(TASK_B.getTaskId(), Protos.TaskState.TASK_ERROR));
        stateStore.storeStatus(TASK_C.getName(), TaskTestUtils.generateStatus(TASK_C.getTaskId(), Protos.TaskState.TASK_ERROR));

        UninstallScheduler uninstallScheduler = getUninstallScheduler();

        // Invoke getClientStatus so that plan status is correctly processed before offers are passed:
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());

        Plan plan = getUninstallPlan(uninstallScheduler);
        List<Status> expected = Arrays.asList(
                Status.PENDING, Status.PENDING, Status.PENDING, // 3 task kills
                Status.PENDING, Status.PENDING, Status.PENDING, // 3 resources from task A. nothing from task B or C which are ERROR+permfail
                Status.PENDING); // deregister step
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testUninstallStepsPrepared() throws Exception {
        // Initial call to resourceOffers() will return all steps from resource phase as candidates
        // regardless of the offers sent in, and will start the steps.
        UninstallScheduler uninstallScheduler = getUninstallScheduler();

        // Invoke getClientStatus so that plan status is correctly processed before offers are passed:
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());
        uninstallScheduler.offers(Collections.singletonList(getOffer()));

        Plan plan = getUninstallPlan(uninstallScheduler);
        // 1 task kill + 3 resources + deregister step. The resource steps do not depend on the kill step so they are PREPARED right away.
        List<Status> expected = Arrays.asList(Status.COMPLETE, Status.PREPARED, Status.PREPARED, Status.PREPARED, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
    }

    @Test
    public void testUninstallStepsComplete() throws Exception {
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(RESERVED_RESOURCE_1, RESERVED_RESOURCE_2));
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        // Invoke getClientStatus so that plan status is correctly processed before offers are passed:
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());
        uninstallScheduler.offers(Collections.singletonList(offer));

        // Verify that scheduler doesn't expect _1 and _2. It then expects that they have been cleaned:
        UnexpectedResourcesResponse response =
                uninstallScheduler.getUnexpectedResources(Collections.singletonList(offer));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(1, response.offerResources.size());
        Assert.assertEquals(offer.getResourcesList(), response.offerResources.iterator().next().getResources());

        // Check that _1 and _2 are now marked as complete, while _3 is still prepared:
        Plan plan = getUninstallPlan(uninstallScheduler);
        List<Status> expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PREPARED, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        // Invoke getClientStatus so that plan status is correctly processed before offers are passed.
        // Shouldn't see any new work since all the uninstall steps were considered candidates up-front. 
        Assert.assertEquals(ClientStatusResponse.launching(false), uninstallScheduler.getClientStatus());
        offer = OfferTestUtils.getOffer(Collections.singletonList(RESERVED_RESOURCE_3));
        uninstallScheduler.offers(Collections.singletonList(offer));

        // Verify that scheduler doesn't expect _3. It then expects that it has been cleaned:
        response = uninstallScheduler.getUnexpectedResources(Collections.singletonList(offer));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(1, response.offerResources.size());
        Assert.assertEquals(offer.getResourcesList(), response.offerResources.iterator().next().getResources());

        expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING);
        Assert.assertEquals(expected, getStepStatuses(plan));
    }

    @Test
    public void testPlanCompletes() throws Exception {
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(
                RESERVED_RESOURCE_1, RESERVED_RESOURCE_2, RESERVED_RESOURCE_3));
        UninstallScheduler uninstallScheduler = getUninstallScheduler();
        // Invoke getClientStatus so that plan status is correctly processed before offers are passed:
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());
        uninstallScheduler.offers(Collections.singletonList(offer));

        // Verify that scheduler doesn't expect _1/_2/_3. It then expects that they have been cleaned:
        UnexpectedResourcesResponse response =
                uninstallScheduler.getUnexpectedResources(Collections.singletonList(offer));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(1, response.offerResources.size());
        Assert.assertEquals(offer.getResourcesList(), response.offerResources.iterator().next().getResources());

        Plan plan = getUninstallPlan(uninstallScheduler);
        List<Status> expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        // Turn the crank once to prepare the deregistration Step
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());
        uninstallScheduler.offers(Arrays.asList(getOffer()));
        expected = Arrays.asList(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PREPARED);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
        // Advertise an UNINSTALLED state so that upstream will clean us up and call unregistered():
        Assert.assertEquals(ClientStatusResponse.readyToRemove(), uninstallScheduler.getClientStatus());

        // Deregistration completes only after we've told the scheduler that it's unregistered
        uninstallScheduler.unregistered();
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
                new StateStore(persister),
                mockConfigStore,
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(mockSecretsClient),
                new TestTimeFetcher());
        uninstallScheduler.registered(false);
        // Starts with a near-empty plan with only the deregistered call incomplete
        Plan plan = getUninstallPlan(uninstallScheduler);

        List<Status> expected = Arrays.asList(Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
        Assert.assertTrue(plan.toString(), plan.isRunning());
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());

        // Turn the crank to get the deregistered step to continue
        uninstallScheduler.offers(Arrays.asList(getOffer()));
        Assert.assertEquals(ClientStatusResponse.readyToRemove(), uninstallScheduler.getClientStatus());
        expected = Arrays.asList(Status.PREPARED);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
        Assert.assertTrue(plan.toString(), plan.isRunning());

        // Seal the deal by telling the service it's unregistered
        uninstallScheduler.unregistered();
        Assert.assertEquals(ClientStatusResponse.readyToRemove(), uninstallScheduler.getClientStatus());
        expected = Arrays.asList(Status.COMPLETE);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
        Assert.assertTrue(plan.toString(), plan.isComplete());
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

        UninstallScheduler uninstallScheduler = getUninstallScheduler(serviceSpecWithTLSTasks, new TestTimeFetcher());
        Plan plan = getUninstallPlan(uninstallScheduler);

        when(mockSecretsClient.list(TestConstants.SERVICE_NAME)).thenReturn(Collections.emptyList());

        // Run through the task cleanup and TLS phases
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(
                RESERVED_RESOURCE_1, RESERVED_RESOURCE_2, RESERVED_RESOURCE_3));
        uninstallScheduler.offers(Collections.singletonList(offer));

        // Verify that scheduler doesn't expect _1/_2/_3. It then expects that they have been cleaned:
        UnexpectedResourcesResponse response =
                uninstallScheduler.getUnexpectedResources(Collections.singletonList(offer));
        Assert.assertEquals(UnexpectedResourcesResponse.Result.PROCESSED, response.result);
        Assert.assertEquals(1, response.offerResources.size());
        Assert.assertEquals(offer.getResourcesList(), response.offerResources.iterator().next().getResources());

        // The TLS phase should also have completed, as it doesn't depend on task kill/unreserve:
        List<Status> expected = Arrays.asList(
                Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PENDING);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        verify(mockSecretsClient, times(1)).list(TestConstants.SERVICE_NAME);

        // Then the final Deregister phase: goes PREPARED when offer arrives, then COMPLETE when told that we're unregistered
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());
        uninstallScheduler.offers(Collections.singletonList(getOffer()));
        expected = Arrays.asList(
                Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.PREPARED);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));
        // Advertise an UNINSTALLED state so that upstream will clean us up and call unregistered():
        Assert.assertEquals(ClientStatusResponse.readyToRemove(), uninstallScheduler.getClientStatus());

        uninstallScheduler.unregistered();
        Assert.assertEquals(ClientStatusResponse.readyToRemove(), uninstallScheduler.getClientStatus());
        expected = Arrays.asList(
                Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE, Status.COMPLETE);
        Assert.assertEquals(plan.toString(), expected, getStepStatuses(plan));

        Assert.assertTrue(plan.isComplete());
    }

    @Test
    public void testUninstallPlanCustomizer() throws Exception {
        UninstallScheduler uninstallScheduler = new UninstallScheduler(
                getServiceSpec(),
                stateStore,
                mockConfigStore,
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Optional.of(getReversingPlanCustomizer()),
                Optional.empty(),
                Optional.of(mockSecretsClient),
                new TestTimeFetcher());

        Plan plan = getUninstallPlan(uninstallScheduler);

        // The standard order is kill-tasks, unreserve-resources, deregister-service. Verify the inverse
        // is now true.
        Assert.assertEquals("deregister-service", plan.getChildren().get(0).getName());
        Assert.assertEquals("unreserve-resources-host-1", plan.getChildren().get(1).getName());
        Assert.assertEquals("kill-tasks", plan.getChildren().get(2).getName());
    }

    @Test
    public void testUninstallTimeout() {
        // Rebuild client because timeout is checked in constructor:
        TestTimeFetcher testTimeFetcher = new TestTimeFetcher();
        UninstallScheduler uninstallScheduler = getUninstallScheduler(getServiceSpec(), testTimeFetcher);

        // 0s: starts uninstalling
        Assert.assertEquals(ClientStatusResponse.launching(true), uninstallScheduler.getClientStatus());

        // 60s: not quite timeout yet
        testTimeFetcher.addSeconds(60);
        Assert.assertEquals(ClientStatusResponse.launching(false), uninstallScheduler.getClientStatus());

        // 61s: 'done' due to timeout
        testTimeFetcher.addSeconds(1);
        Assert.assertEquals(ClientStatusResponse.readyToRemove(), uninstallScheduler.getClientStatus());
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
        return getUninstallScheduler(getServiceSpec(), new TestTimeFetcher());
    }

    private UninstallScheduler getUninstallScheduler(ServiceSpec serviceSpec, UninstallScheduler.TimeFetcher timeFetcher) {
        UninstallScheduler uninstallScheduler = new UninstallScheduler(
                serviceSpec,
                stateStore,
                mockConfigStore,
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Optional.of(mockPlanCustomizer),
                Optional.empty(),
                Optional.of(mockSecretsClient),
                timeFetcher);
        uninstallScheduler.registered(false);
        return uninstallScheduler;
    }

    private static Plan getUninstallPlan(AbstractScheduler scheduler) {
        return scheduler.getPlanCoordinator().getPlanManagers().stream().findFirst().get().getPlan();
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

    private static class TestTimeFetcher extends UninstallScheduler.TimeFetcher {
        private long currentTimeSeconds = 1234567890; // Feb 13 2009

        private void addSeconds(long secs) {
            currentTimeSeconds += secs;
        }

        @Override
        protected long getCurrentTimeMillis() {
            return currentTimeSeconds * 1000;
        }
    }
}
