package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.ResourceRefinementCapabilityContext;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.PersistentLaunchRecorder;
import com.mesosphere.sdk.testutils.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class OfferEvaluatorTest extends OfferEvaluatorTestBase {
    private @Mock ServiceSpec serviceSpec;

    @Test
    public void testReserveLaunchScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedCpus(2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredResource)));
        Assert.assertEquals(6, recommendations.size());

        // Validate RESERVE Operation
        Protos.Offer.Operation reserveOperation = recommendations.get(3).getOperation().get();
        Protos.Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Protos.Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(36, getResourceId(reserveResource).length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(4).getOperation().get();
        Protos.Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(getResourceId(reserveResource), getResourceId(launchResource));

        Protos.ExecutorID executorId = launchOperation.getLaunchGroup().getExecutor().getExecutorId();
        Assert.assertEquals(TestConstants.POD_TYPE, CommonIdUtils.toExecutorName(executorId));
    }

    private Collection<Protos.Resource> getExpectedExecutorResources(Protos.ExecutorInfo executorInfo) {
        String executorCpuId = executorInfo.getResourcesList().stream()
                .filter(r -> r.getName().equals("cpus"))
                .map(ResourceUtils::getResourceId)
                .filter(o -> o.isPresent())
                .map(o -> o.get())
                .findFirst()
                .get();
        String executorMemId = executorInfo.getResourcesList().stream()
                .filter(r -> r.getName().equals("mem"))
                .map(ResourceUtils::getResourceId)
                .filter(o -> o.isPresent())
                .map(o -> o.get())
                .findFirst()
                .get();
        String executorDiskId = executorInfo.getResourcesList().stream()
                .filter(r -> r.getName().equals("disk"))
                .map(ResourceUtils::getResourceId)
                .filter(o -> o.isPresent())
                .map(o -> o.get())
                .findFirst()
                .get();

        Protos.Resource expectedExecutorCpu = ResourceTestUtils.getReservedCpus(0.1, executorCpuId);
        Protos.Resource expectedExecutorMem = ResourceTestUtils.getReservedMem(32, executorMemId);
        Protos.Resource expectedExecutorDisk = ResourceTestUtils.getReservedDisk(256, executorDiskId);

        return new ArrayList<>(Arrays.asList(expectedExecutorCpu, expectedExecutorMem, expectedExecutorDisk));
    }

    @Test
    public void testReserveLaunchScalarRefined() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testReserveLaunchScalar();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testRelaunchExpectedScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        // Launch for the first time.
        String resourceId = getResourceId(
                recordLaunchWithCompleteOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(2.0)).get(3));

        // Launch again on expected resources.
        Collection<Protos.Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getReservedCpus(1.0, resourceId));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(2, recommendations.size());

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(0).getOperation().get();
        Protos.Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testIncreaseReservationScalar() throws Exception {
        // Launch for the first time with 2.0 cpus offered, 1.0 cpus required.
        String resourceId = getResourceId(
                recordLaunchWithCompleteOfferedResources(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0),
                        ResourceTestUtils.getUnreservedCpus(2.0)).get(3));

        // Launch again with 1.0 cpus reserved, 1.0 cpus unreserved, and 2.0 cpus required.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        Protos.Resource offeredResource = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        Protos.Resource unreservedResource = ResourceTestUtils.getUnreservedCpus(1.0);

        Collection<Protos.Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(offeredResource, unreservedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(3, recommendations.size());

        // Validate RESERVE Operation
        Protos.Offer.Operation reserveOperation = recommendations.get(0).getOperation().get();
        Protos.Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Protos.Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(reserveResource));

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation().get();
        Protos.Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);

        // Validate storage recommendation (no operation)
        Assert.assertFalse(recommendations.get(2).getOperation().isPresent());
    }

    @Test
    public void testIncreaseReservationScalarRefined() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testIncreaseReservationScalar();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testIncreasePreReservedReservationScalar() throws Exception {
        final String preReservedRole = "slave_public";

        // Launch for the first time with 2.0 cpus offered, 1.0 cpus required.
        String resourceId = getResourceId(
                recordLaunchWithCompleteOfferedResources(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0, preReservedRole),
                        preReservedRole,
                        ResourceTestUtils.getUnreservedCpus(2.0, preReservedRole)).get(3));

        // Launch again with 1.0 cpus reserved, 1.0 cpus unreserved, and 2.0 cpus required.
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getCpuRequirement(2.0, preReservedRole);
        Protos.Resource offeredResource = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        Protos.Resource unreservedResource = ResourceTestUtils.getUnreservedCpus(1.0, preReservedRole);

        Collection<Protos.Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(offeredResource, unreservedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(3, recommendations.size());

        // Validate RESERVE Operation
        Protos.Offer.Operation reserveOperation = recommendations.get(0).getOperation().get();
        Protos.Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Protos.Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(reserveResource));

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation().get();
        Protos.Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testDecreaseReservationScalar() throws Exception {
        // Launch for the first time.
        Protos.Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                PodInstanceRequirementTestUtils.getCpuRequirement(2.0),
                ResourceTestUtils.getUnreservedCpus(2.0))
                .get(3);
        String resourceId = getResourceId(reserveResource);
        Collection<Protos.Resource> offeredResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());

        // Launch again with fewer resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Protos.Resource offeredResource = ResourceTestUtils.getReservedCpus(2.0, resourceId);
        Protos.Resource unreservedResource = ResourceTestUtils.getUnreservedCpus(1.0);
        offeredResources.addAll(Arrays.asList(offeredResource, unreservedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredResources)));
        Assert.assertEquals(3, recommendations.size());

        // Validate UNRESERVE Operation
        Protos.Offer.Operation unreserveOperation = recommendations.get(0).getOperation().get();
        Protos.Resource unreserveResource = unreserveOperation.getUnreserve().getResources(0);

        Protos.Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Protos.Offer.Operation.Type.UNRESERVE, unreserveOperation.getType());
        Assert.assertEquals(1.0, unreserveResource.getScalar().getValue(), 0.0);
        validateRole(unreserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(unreserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(unreserveResource));

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation().get();
        Protos.Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testDecreaseReservationScalarRefined() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testDecreaseReservationScalar();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testFailIncreaseReservationScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        Protos.Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                podInstanceRequirement,
                ResourceTestUtils.getUnreservedCpus(2.0))
                .get(3);
        String resourceId = getResourceId(reserveResource);

        Protos.Resource offeredResource = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testLaunchAttributesEmbedded() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        // Launch for the first time.
        String resourceId = getResourceId(
                recordLaunchWithCompleteOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(2.0)).get(3));

        Collection<Protos.Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getReservedCpus(1.0, resourceId));

        Protos.Offer.Builder offerBuilder = OfferTestUtils.getOffer(expectedResources).toBuilder();
        Protos.Attribute.Builder attrBuilder =
                offerBuilder.addAttributesBuilder().setName("rack").setType(Protos.Value.Type.TEXT);
        attrBuilder.getTextBuilder().setValue("foo");
        attrBuilder = offerBuilder.addAttributesBuilder().setName("diskspeed").setType(Protos.Value.Type.SCALAR);
        attrBuilder.getScalarBuilder().setValue(1234.5678);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(offerBuilder.build()));
        Assert.assertEquals(2, recommendations.size());

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(0).getOperation().get();
        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, launchOperation.getType());

        // Validate state update operation
        Assert.assertFalse(recommendations.get(1).getOperation().isPresent());

        // Validate that TaskInfo has embedded the Attributes from the selected offer:
        Protos.TaskInfo launchTask = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        Assert.assertEquals(
                Arrays.asList("rack:foo", "diskspeed:1234.568"),
                new TaskLabelReader(launchTask).getOfferAttributeStrings());
        Protos.Resource launchResource = launchTask.getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedCpus(3.0);

        ResourceSet resourceSetA = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .cpus(1.0)
                .id("resourceSetA")
                .build();
        ResourceSet resourceSetB = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .cpus(2.0)
                .id("resourceSetB")
                .build();

        CommandSpec commandSpec = DefaultCommandSpec.newBuilder(Collections.emptyMap())
                .value("./cmd")
                .build();

        TaskSpec taskSpecA = DefaultTaskSpec.newBuilder()
                .name("taskA")
                .commandSpec(commandSpec)
                .goalState(GoalState.RUNNING)
                .resourceSet(resourceSetA)
                .build();
        TaskSpec taskSpecB = DefaultTaskSpec.newBuilder()
                .name("taskB")
                .commandSpec(commandSpec)
                .goalState(GoalState.RUNNING)
                .resourceSet(resourceSetB)
                .build();

        PodSpec podSpec =
                DefaultPodSpec.newBuilder(
                        TestConstants.POD_TYPE,
                        1,
                        Arrays.asList(taskSpecA, taskSpecB))
                        .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("taskA", "taskB"))
                        .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredResource)));

        Assert.assertEquals(Arrays.asList(
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                // Validate node task operations
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null,
                // Validate format task operations
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));

        // TaskInfo.executor is unset in LAUNCH operations, instead it's set at the LaunchGroup level:

        Protos.Offer.Operation operation = recommendations.get(4).getOperation().get();

        Protos.TaskInfo launchTask = operation.getLaunchGroup().getTaskGroup().getTasks(0);
        Assert.assertFalse(launchTask.hasExecutor());
        Assert.assertEquals("pod-type-0-taskA", launchTask.getName());

        Assert.assertTrue(operation.getLaunchGroup().hasExecutor());
        Protos.ExecutorInfo launch0Executor = operation.getLaunchGroup().getExecutor();
        Assert.assertEquals("pod-type", launch0Executor.getName());

        operation = recommendations.get(7).getOperation().get();

        launchTask = operation.getLaunchGroup().getTaskGroup().getTasks(0);
        Assert.assertFalse(launchTask.hasExecutor());
        Assert.assertEquals("pod-type-0-taskB", launchTask.getName());

        Assert.assertTrue(operation.getLaunchGroup().hasExecutor());
        Protos.ExecutorInfo launch1Executor = operation.getLaunchGroup().getExecutor();
        Assert.assertEquals("pod-type", launch1Executor.getName());

        Assert.assertEquals(launch0Executor, launch1Executor);

        // Meanwhile in the update TaskInfos, TaskInfo.executor is set:

        Assert.assertEquals(launch0Executor, ((StoreTaskInfoRecommendation) recommendations.get(5)).getStateStoreTaskInfo().getExecutor());
        Assert.assertEquals(launch1Executor, ((StoreTaskInfoRecommendation) recommendations.get(8)).getStateStoreTaskInfo().getExecutor());
    }

    @Test
    public void testLaunchNotOnFirstOffer() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Protos.Resource insufficientOffer = ResourceTestUtils.getUnreservedMem(2.0);
        Protos.Resource sufficientOffer = ResourceTestUtils.getUnreservedCpus(2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(
                        OfferTestUtils.getCompleteOffer(insufficientOffer),
                        OfferTestUtils.getCompleteOffer(sufficientOffer)));

        Assert.assertEquals(Arrays.asList(
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));
    }

    @Test
    public void testLaunchSequencedTasksInPod() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("resource-set-seq.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement formatPodInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Protos.Offer sufficientUnreservedOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        // Launch Task with ONCE goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                formatPodInstanceRequirement,
                Arrays.asList(sufficientUnreservedOffer));

        Assert.assertEquals(Arrays.asList(
                // Executor reservations:
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                // Reservations + TaskInfo storage for backup task (ResourceSet=sidecar-resources))
                Protos.Offer.Operation.Type.RESERVE,
                null,
                // Reservations + TaskInfo storage for bootstrap, format(+launch), and node tasks (ResourceSet=name-resources)
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.CREATE,
                null,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));

        // Validate "backup" task storage (no launch)
        StoreTaskInfoRecommendation backupStoreRecommendation = (StoreTaskInfoRecommendation) recommendations.get(4);
        Assert.assertEquals("name-0-backup", backupStoreRecommendation.getStateStoreTaskInfo().getName());

        // Validate "bootstrap" task storage (no launch)
        StoreTaskInfoRecommendation bootstrapStoreRecommendation = (StoreTaskInfoRecommendation) recommendations.get(8);
        Assert.assertEquals("name-0-bootstrap", bootstrapStoreRecommendation.getStateStoreTaskInfo().getName());

        // Validate "format" task launch + storage
        Protos.Offer.Operation formatLaunchOperation =
                ((LaunchOfferRecommendation) recommendations.get(9)).getOperation().get();
        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, formatLaunchOperation.getType());
        Assert.assertEquals("name-0-format", formatLaunchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getName());

        StoreTaskInfoRecommendation formatStoreRecommendation = (StoreTaskInfoRecommendation) recommendations.get(10);
        Assert.assertEquals("name-0-format", formatStoreRecommendation.getStateStoreTaskInfo().getName());

        // Validate "node" task storage (no launch)
        StoreTaskInfoRecommendation nodeStoreRecommendation = (StoreTaskInfoRecommendation) recommendations.get(11);
        Assert.assertEquals("name-0-node", nodeStoreRecommendation.getStateStoreTaskInfo().getName());

        recordOperations(recommendations);

        // Launch name-0-node task, later.
        PodInstanceRequirement nodePodInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();
        recommendations = evaluator.evaluate(nodePodInstanceRequirement, Arrays.asList(sufficientUnreservedOffer));
        // Providing sufficient, but unreserved resources should result in no operations.
        Assert.assertEquals(0, recommendations.size());

        // Grab the resource IDs from name-0-format to be reoffered below. The format task shares the "name-resources"
        // ResourceSet with name-0-node, so its resources will be sufficient to launch that.
        Protos.TaskInfo formatTaskToReoffer = formatStoreRecommendation.getStateStoreTaskInfo();

        Collection<Protos.Resource> expectedResources = getExpectedExecutorResources(formatTaskToReoffer.getExecutor());
        expectedResources.addAll(Arrays.asList(
                ResourceTestUtils.getReservedCpus(
                        1.0,
                        ResourceTestUtils.getResourceId(formatTaskToReoffer.getResources(0))),
                ResourceTestUtils.getReservedRootVolume(
                        50.0,
                        ResourceTestUtils.getResourceId(formatTaskToReoffer.getResources(1)),
                        ResourceTestUtils.getPersistenceId(formatTaskToReoffer.getResources(1)))));

        Protos.Offer offer = OfferTestUtils.getOffer(expectedResources).toBuilder()
                .addExecutorIds(formatTaskToReoffer.getExecutor().getExecutorId())
                .build();
        recommendations = evaluator.evaluate(nodePodInstanceRequirement, Arrays.asList(offer));

        // All tasks in the "name-resources" ResourceSet should get a TaskInfo update, and the "node" task should also get a launch:
        Assert.assertEquals(Arrays.asList(
                null,
                null,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));

        // Validate "bootstrap" task storage (no launch)
        bootstrapStoreRecommendation = (StoreTaskInfoRecommendation) recommendations.get(0);
        Assert.assertEquals("name-0-bootstrap", bootstrapStoreRecommendation.getStateStoreTaskInfo().getName());

        // Validate "format" task storage (no launch)
        formatStoreRecommendation = (StoreTaskInfoRecommendation) recommendations.get(1);
        Assert.assertEquals("name-0-format", formatStoreRecommendation.getStateStoreTaskInfo().getName());

        // Validate "node" task launch + storage
        Protos.Offer.Operation nodeLaunchOperation = recommendations.get(2).getOperation().get();
        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH_GROUP, nodeLaunchOperation.getType());
        Assert.assertEquals("name-0-node", nodeLaunchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getName());

        nodeStoreRecommendation = (StoreTaskInfoRecommendation) recommendations.get(3);
        Assert.assertEquals("name-0-node", nodeStoreRecommendation.getStateStoreTaskInfo().getName());
    }

    @Test
    public void testRelaunchFailedPod() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("resource-set-seq.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Protos.Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        // Launch Task with ONCE goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(Arrays.asList(
                // Executor creation
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                // backup task (ResourceSet=sidecar-resources) -- no launch
                Protos.Offer.Operation.Type.RESERVE,
                null,
                // bootstrap+format+node tasks (ResourceSet=name-resources) -- only format is launched
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.CREATE,
                null,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));

        Assert.assertEquals("name-0-backup", ((StoreTaskInfoRecommendation) recommendations.get(4)).getStateStoreTaskInfo().getName());

        Assert.assertEquals("name-0-bootstrap", ((StoreTaskInfoRecommendation) recommendations.get(8)).getStateStoreTaskInfo().getName());

        Assert.assertEquals("name-0-format", recommendations.get(9).getOperation().get().getLaunchGroup().getTaskGroup().getTasks(0).getName());
        Assert.assertEquals("name-0-format", ((StoreTaskInfoRecommendation) recommendations.get(10)).getStateStoreTaskInfo().getName());

        Assert.assertEquals("name-0-node", ((StoreTaskInfoRecommendation) recommendations.get(11)).getStateStoreTaskInfo().getName());

        recordOperations(recommendations);

        // Attempt to launch task again as non-failed.
        podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // The pod is running fine according to the state store, so no new deployment is issued.
        Assert.assertEquals(recommendations.toString(), 0, recommendations.size());

        // Now the same operation except with the task flagged as having permanently failed.
        podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node"))
                .recoveryType(RecoveryType.PERMANENT)
                .build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));

        // A new deployment replaces the prior one above.
        Assert.assertEquals(Arrays.asList(
                // Executor creation
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                // backup task (ResourceSet=sidecar-resources) -- no launch
                Protos.Offer.Operation.Type.RESERVE,
                null,
                // bootstrap+format+node tasks (ResourceSet=name-resources) -- only node is launched
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.CREATE,
                null,
                null,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));

        Assert.assertEquals("name-0-backup", ((StoreTaskInfoRecommendation) recommendations.get(4)).getStateStoreTaskInfo().getName());

        Assert.assertEquals("name-0-bootstrap", ((StoreTaskInfoRecommendation) recommendations.get(8)).getStateStoreTaskInfo().getName());

        Assert.assertEquals("name-0-format", ((StoreTaskInfoRecommendation) recommendations.get(9)).getStateStoreTaskInfo().getName());

        Assert.assertEquals("name-0-node", recommendations.get(10).getOperation().get().getLaunchGroup().getTaskGroup().getTasks(0).getName());
        Assert.assertEquals("name-0-node", ((StoreTaskInfoRecommendation) recommendations.get(11)).getStateStoreTaskInfo().getName());
    }

    @Test
    public void testTransientToPermanentFailure() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("resource-set-seq.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();

        Protos.Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        // Launch Task with RUNNING goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(Arrays.asList(
                // Executor creation
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                // backup task (ResourceSet=sidecar-resources) -- no launch
                Protos.Offer.Operation.Type.RESERVE,
                null,
                // bootstrap+format+node tasks (ResourceSet=name-resources) -- only node is launched
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.CREATE,
                null,
                null,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));

        recordOperations(recommendations);

        // Fail the task due to a lost Agent
        Protos.TaskInfo taskInfo = stateStore.fetchTask(TaskUtils.getTaskNames(podInstance).get(0)).get();
        final Protos.TaskStatus failedStatus = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_LOST);
        stateStore.storeStatus(taskInfo.getName(), failedStatus);

        // Mark the pod instance as permanently failed.
        FailureUtils.setPermanentlyFailed(stateStore, podInstance);
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));

        // A new deployment replaces the prior one above.
        Assert.assertEquals(Arrays.asList(
                // Executor creation
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                // backup task (ResourceSet=sidecar-resources) -- no launch
                Protos.Offer.Operation.Type.RESERVE,
                null,
                // bootstrap+format+node tasks (ResourceSet=name-resources) -- only node is launched
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.CREATE,
                null,
                null,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));
    }

    @Test
    public void testReplaceDeployStep() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("valid-minimal-volume.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("task-name")).build();
        DeploymentStep deploymentStep =
                new DeploymentStep("test-step", podInstanceRequirement, stateStore, Optional.empty());

        Protos.Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedMem(1024),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                deploymentStep.getPodInstanceRequirement().get(),
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 9, recommendations.size());
        Protos.Offer.Operation launchOperation = recommendations.get(7).getOperation().get();
        Protos.TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        recordOperations(recommendations);

        deploymentStep.updateOfferStatus(recommendations);
        Assert.assertEquals(com.mesosphere.sdk.scheduler.plan.Status.STARTING, deploymentStep.getStatus());

        // Simulate an initial failure to deploy.  Perhaps the CREATE operation failed
        deploymentStep.update(
                Protos.TaskStatus.newBuilder()
                        .setTaskId(taskInfo.getTaskId())
                        .setState(Protos.TaskState.TASK_ERROR)
                        .build());

        Assert.assertEquals(com.mesosphere.sdk.scheduler.plan.Status.PENDING, deploymentStep.getStatus());

        // Simulate a replace call by marking (all tasks in) the pod as permanently failed:
        FailureUtils.setPermanentlyFailed(stateStore, deploymentStep.getPodInstanceRequirement().get().getPodInstance());

        Assert.assertTrue(FailureUtils.isPermanentlyFailed(stateStore.fetchTask(taskInfo.getName()).get()));

        recommendations = evaluator.evaluate(
                deploymentStep.getPodInstanceRequirement().get(),
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(Arrays.asList(
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                // Validate format task operations
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.RESERVE,
                Protos.Offer.Operation.Type.CREATE,
                Protos.Offer.Operation.Type.LAUNCH_GROUP,
                null),
                recommendations.stream()
                        .map(rec -> rec.getOperation().isPresent() ? rec.getOperation().get().getType() : null)
                        .collect(Collectors.toList()));
    }

    @SuppressWarnings("deprecated")
    @Test
    public void testResourceRefinementSucceeds() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            ServiceSpec serviceSpec = getServiceSpec("resource-refinement.yml");
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Protos.Offer sufficientOffer = OfferTestUtils.getCompleteOffer(
                    Arrays.asList(
                            // Include executor resources.
                            ResourceTestUtils.getUnreservedCpus(0.1),
                            ResourceTestUtils.getUnreservedMem(256),
                            ResourceTestUtils.getUnreservedDisk(512),
                            ResourceTestUtils.getUnreservedCpus(3.0)).stream()
                            .map(r -> r.toBuilder()
                                    .setRole(Constants.ANY_ROLE)
                                    .addReservations(
                                            Protos.Resource.ReservationInfo.newBuilder()
                                                    .setRole(TestConstants.PRE_RESERVED_ROLE)
                                                    .setType(Protos.Resource.ReservationInfo.Type.STATIC))
                                    .build())
                            .collect(Collectors.toList()));

            PodSpec podSpec = serviceSpec.getPods().get(0);
            PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
            List<String> tasksToLaunch = Arrays.asList("test-task");
            PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch)
                    .build();

            List<OfferRecommendation> recommendations = evaluator.evaluate(
                    podInstanceRequirement,
                    Arrays.asList(sufficientOffer));
            Assert.assertEquals(6, recommendations.size());

            Protos.Offer.Operation reserveOperation = recommendations.get(0).getOperation().get();
            Protos.Resource reserveResource = reserveOperation.getReserve().getResources(0);
            Assert.assertEquals(2, reserveResource.getReservationsCount());

            Protos.Resource.ReservationInfo preReservation = reserveResource.getReservations(0);
            Assert.assertEquals(Protos.Resource.ReservationInfo.Type.STATIC, preReservation.getType());
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, preReservation.getRole());
            Assert.assertFalse(preReservation.hasLabels());

            Protos.Resource.ReservationInfo dynamicReservation = reserveResource.getReservations(1);
            Assert.assertEquals(Protos.Resource.ReservationInfo.Type.DYNAMIC, dynamicReservation.getType());
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE + "/hello-world-role", dynamicReservation.getRole());
            Assert.assertTrue(dynamicReservation.hasLabels());
        } finally {
            context.reset();
        }
    }

    @Test
    public void testResourceRefinementFailsForMissingPreReservation() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            ServiceSpec serviceSpec = getServiceSpec("resource-refinement.yml");
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Protos.Offer badOffer = OfferTestUtils.getOffer(
                    Arrays.asList(ResourceTestUtils.getUnreservedCpus(3.0)));

            PodSpec podSpec = serviceSpec.getPods().get(0);
            PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
            List<String> tasksToLaunch = TaskUtils.getTaskNames(podInstance);
            PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch)
                    .build();

            List<OfferRecommendation> recommendations = evaluator.evaluate(
                    podInstanceRequirement,
                    Arrays.asList(badOffer));

            Assert.assertEquals(0, recommendations.size());
        } finally {
            context.reset();
        }
    }

    @SuppressWarnings("deprecated")
    @Test
    public void testResourceRefinementFailsForDifferentPreReservation() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            ServiceSpec serviceSpec = getServiceSpec("resource-refinement.yml");
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Protos.Offer badOffer = OfferTestUtils.getOffer(
                    Arrays.asList(
                            ResourceTestUtils.getUnreservedCpus(3.0).toBuilder()
                                    .setRole(Constants.ANY_ROLE)
                                    .addReservations(
                                            Protos.Resource.ReservationInfo.newBuilder()
                                                    .setRole("different-role")
                                                    .setType(Protos.Resource.ReservationInfo.Type.STATIC))
                                    .build()));

            PodSpec podSpec = serviceSpec.getPods().get(0);
            PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
            List<String> tasksToLaunch = Arrays.asList("test-task");
            PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch)
                    .build();

            List<OfferRecommendation> recommendations = evaluator.evaluate(
                    podInstanceRequirement,
                    Arrays.asList(badOffer));

            Assert.assertEquals(0, recommendations.size());
        } finally {
            context.reset();
        }
    }

    /**
     * If recovery is NOT taking place, the target configuration defined in the ConfigStore
     * which is used to construct the OfferEvaluator should be used.
     */
    @Test
    public void testGetTargetConfigRecoveryTypeNone() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance(),
                        Collections.singleton(TestConstants.TASK_NAME))
                        .recoveryType(RecoveryType.NONE)
                        .build();

        Assert.assertEquals(
                targetConfig,
                evaluator.getTargetConfig(
                        podInstanceRequirement,
                        Collections.singletonMap(
                                TestConstants.POD_TYPE + "-0-" + TestConstants.TASK_NAME,
                                TestConstants.TASK_INFO)));
    }

    /**
     * If recovery is taking place but no Tasks have ever been launched in this pod (logical impossibility),
     * the target configuration defined in the ConfigStore which is used to construct the OfferEvaluator should be used.
     */
    @Test
    public void testGetTargetConfigRecoveryEmptyTaskCollection() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        Assert.assertEquals(
                targetConfig,
                evaluator.getTargetConfig(podInstanceRequirement, Collections.emptyMap()));
    }

    /**
     * If recovery is taking place and the task(s) to be recovered lack a config id, the ConfigStore target config
     * should be used.
     */
    @Test
    public void testGetTargetConfigRecoveryMissingConfigId() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance(),
                        Collections.singleton(TestConstants.TASK_NAME))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        Assert.assertEquals(
                targetConfig,
                evaluator.getTargetConfig(
                        podInstanceRequirement,
                        Collections.singletonMap(
                                TestConstants.POD_TYPE + "-0-" + TestConstants.TASK_NAME,
                                TestConstants.TASK_INFO)));
    }

    /**
     * If recovery is taking place and the task(s) to be recovered aren't present in the StateStore, the ConfigStore
     * target config should be used.
     */
    @Test
    public void testGetTargetConfigRecoveryMissingTaskToLaunch() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        UUID taskConfig = UUID.randomUUID();
        Protos.TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder().setLabels(
                new TaskLabelWriter(TestConstants.TASK_INFO)
                        .setTargetConfiguration(taskConfig)
                        .toProto())
                .build();

        Assert.assertEquals(
                targetConfig,
                evaluator.getTargetConfig(
                        podInstanceRequirement,
                        Collections.singletonMap("somethingElse", taskInfo)));
    }

    /**
     * If recovery is taking place and a target config is properly set on the task, its target config should
     * be used, not the ConfigStore / OfferEvaluator's target config.
     */
    @Test
    public void testGetTargetConfigRecoverySingleTask() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance(),
                        Collections.singleton(TestConstants.TASK_NAME))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        UUID taskConfig = UUID.randomUUID();
        Protos.TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder().setLabels(
                new TaskLabelWriter(TestConstants.TASK_INFO)
                        .setTargetConfiguration(taskConfig)
                        .toProto())
                .build();

        Assert.assertEquals(
                taskConfig,
                evaluator.getTargetConfig(
                        podInstanceRequirement,
                        Collections.singletonMap(
                                TestConstants.POD_TYPE + "-0-" + TestConstants.TASK_NAME,
                                taskInfo)));
    }

    /**
     * If a subset of a pod is being recovered, only the config from the tasks to be recovered should be used.
     */
    @Test
    public void testGetTargetConfigRecoveryMixedInclusion() {
        // Create PodSpec with default and "other" tasks:
        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .addTask(DefaultTaskSpec.newBuilder(podSpec.getTasks().get(0))
                        .name("other")
                        .build())
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList(TestConstants.TASK_NAME))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        UUID recoverTaskConfig = UUID.randomUUID();
        String recoverTaskFullName = TaskSpec.getInstanceName(podInstance, TestConstants.TASK_NAME);
        Protos.TaskInfo recoverTaskInfo = TestConstants.TASK_INFO.toBuilder()
                .setName(recoverTaskFullName)
                .setLabels(new TaskLabelWriter(TestConstants.TASK_INFO)
                        .setTargetConfiguration(recoverTaskConfig)
                        .toProto())
                .build();

        UUID otherTaskConfig = UUID.randomUUID();
        String otherTaskFullName = TaskSpec.getInstanceName(podInstance, "other");
        Protos.TaskInfo otherTaskInfo = TestConstants.TASK_INFO.toBuilder()
                .setName(otherTaskFullName)
                .setLabels(new TaskLabelWriter(TestConstants.TASK_INFO)
                        .setTargetConfiguration(otherTaskConfig)
                        .toProto())
                .build();

        Map<String, Protos.TaskInfo> podTasks = new HashMap<>();
        podTasks.put(recoverTaskFullName, recoverTaskInfo);
        podTasks.put(otherTaskFullName, otherTaskInfo);
        Assert.assertEquals(recoverTaskConfig, evaluator.getTargetConfig(podInstanceRequirement, podTasks));
    }

    /**
     * If multiple tasks are being recovered, the config on RUNNING task(s) should get priority over non-RUNNING tasks.
     */
    @Test
    public void testGetTargetConfigRecoveryMixedGoalStates() {
        // Create PodSpec with default=RUNNING and "other"=ONCE tasks:
        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .addTask(DefaultTaskSpec.newBuilder(podSpec.getTasks().get(0))
                        .name("other")
                        .goalState(GoalState.ONCE)
                        .build())
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList(TestConstants.TASK_NAME, "other"))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        UUID recoverTaskConfig = UUID.randomUUID();
        String recoverTaskFullName = TaskSpec.getInstanceName(podInstance, TestConstants.TASK_NAME);
        Protos.TaskInfo recoverTaskInfo = TestConstants.TASK_INFO.toBuilder()
                .setName(recoverTaskFullName)
                .setLabels(new TaskLabelWriter(TestConstants.TASK_INFO)
                        .setTargetConfiguration(recoverTaskConfig)
                        .toProto())
                .build();

        UUID otherTaskConfig = UUID.randomUUID();
        String otherTaskFullName = TaskSpec.getInstanceName(podInstance, "other");
        Protos.TaskInfo otherTaskInfo = TestConstants.TASK_INFO.toBuilder()
                .setName(otherTaskFullName)
                .setLabels(new TaskLabelWriter(TestConstants.TASK_INFO)
                        .setTargetConfiguration(otherTaskConfig)
                        .toProto())
                .build();

        Map<String, Protos.TaskInfo> podTasks = new HashMap<>();
        podTasks.put(recoverTaskFullName, recoverTaskInfo);
        podTasks.put(otherTaskFullName, otherTaskInfo);
        Assert.assertEquals(recoverTaskConfig, evaluator.getTargetConfig(podInstanceRequirement, podTasks));
    }

    @Test
    public void testLogOutcomeSingleChild() {
        EvaluationOutcome child = EvaluationOutcome.pass(this, "CHILD").build();
        EvaluationOutcome parent = EvaluationOutcome
                .pass(this, "PARENT")
                .addChild(child)
                .build();

        StringBuilder builder = new StringBuilder();
        OfferEvaluator.logOutcome(builder, parent, "");
        String log = builder.toString();
        Assert.assertEquals("  PASS(OfferEvaluatorTest): PARENT\n    PASS(OfferEvaluatorTest): CHILD\n", log);
    }

    @Test
    public void testLogOutcomeMultiChild() {
        EvaluationOutcome child = EvaluationOutcome.pass(this, "CHILD").build();
        EvaluationOutcome parent = EvaluationOutcome
                .pass(this, "PARENT")
                .addAllChildren(Arrays.asList(child))
                .build();

        StringBuilder builder = new StringBuilder();
        OfferEvaluator.logOutcome(builder, parent, "");
        String log = builder.toString();
        Assert.assertEquals("  PASS(OfferEvaluatorTest): PARENT\n    PASS(OfferEvaluatorTest): CHILD\n", log);
    }

    @Test
    public void testEvaluationPipelineGeneratesSingleTLSEvaluationPerTask() throws IOException {
        Pair<PodInstanceRequirement, List<String>> podInfo = getRequirementWithTransportEncryption(
                PodInstanceRequirementTestUtils.getCpuResourceSet(1.0),
                TestConstants.POD_TYPE,
                0,
                2);

        List<OfferEvaluationStage> evaluators = evaluator.getEvaluationPipeline(podInfo.getLeft(),
                                        new ArrayList<>(),
                                        new HashMap<>());

        List<String> tlsEvaluationTasks = evaluators.stream()
                .filter(e -> e instanceof TLSEvaluationStage)
                .map(e -> ((TLSEvaluationStage) e))
                .map(t -> t.getTaskName())
                .sorted()
                .collect(Collectors.toList());

        Assert.assertEquals(podInfo.getRight(), tlsEvaluationTasks);

    }

    private static Pair<PodInstanceRequirement, List<String>> getRequirementWithTransportEncryption(
            ResourceSet resourceSet, String type, int index, int numberOfTasks) {

        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(DefaultTransportEncryptionSpec.newBuilder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        List<TaskSpec> taskSpecs = new ArrayList<>();
        for (int i = 0; i < numberOfTasks; ++i) {
            taskSpecs.add(
                    DefaultTaskSpec.newBuilder()
                            .name(String.format("%s%d", TestConstants.TASK_NAME, i))
                            .commandSpec(
                                    DefaultCommandSpec.newBuilder(Collections.emptyMap())
                                            .value(TestConstants.TASK_CMD)
                                            .build())
                            .goalState(GoalState.RUNNING)
                            .resourceSet(resourceSet)
                            .setTransportEncryption(transportEncryptionSpecs)
                            .build()
            );
        }

        PodSpec podSpec = DefaultPodSpec.newBuilder(type, 1, taskSpecs)
                .preReservedRole(Constants.ANY_ROLE)
                .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, index);
        List<String> taskNames = podInstance.getPod().getTasks().stream()
                .map(ts -> ts.getName())
                .sorted()
                .collect(Collectors.toList());
        return Pair.of(
                PodInstanceRequirement.newBuilder(podInstance, taskNames).build(),
                taskNames
        );
    }

    private void recordOperations(List<OfferRecommendation> recommendations) throws Exception {
        new PersistentLaunchRecorder(stateStore, serviceSpec, Optional.empty()).record(recommendations);
    }

    private ServiceSpec getServiceSpec(String specFileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(specFileName).getFile());
        return DefaultServiceSpec.newGenerator(file, SchedulerConfigTestUtils.getTestSchedulerConfig()).build();
    }

    @SuppressWarnings("deprecated")
    static void validateRole(Protos.Resource resource) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        } else {
            Assert.assertEquals(TestConstants.ROLE, resource.getRole());
        }
    }
}
