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
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class OfferEvaluatorTest extends OfferEvaluatorTestBase {
    @Mock ServiceSpec serviceSpec;

    @Test
    public void testReserveLaunchScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource offeredResource = ResourceTestUtils.getUnreservedCpus(2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredResource)));
        Assert.assertEquals(5, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(36, getResourceId(reserveResource).length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(4).getOperation();
        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(getResourceId(reserveResource), getResourceId(launchResource));
        String executorId = launchOperation.getLaunchGroup().getExecutor().getExecutorId().getValue();

        String prefix = TestConstants.POD_TYPE + CommonIdUtils.NAME_ID_DELIM;
        Assert.assertTrue(executorId.startsWith(prefix));
        Assert.assertEquals(prefix.length() + UUID.randomUUID().toString().length(), executorId.length());
    }

    private Collection<Resource> getExpectedExecutorResources(ExecutorInfo executorInfo) {
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

        Resource expectedExecutorCpu = ResourceTestUtils.getReservedCpus(0.1, executorCpuId);
        Resource expectedExecutorMem = ResourceTestUtils.getReservedMem(32, executorMemId);
        Resource expectedExecutorDisk = ResourceTestUtils.getReservedDisk(256, executorDiskId);

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
        String resourceId = getFirstResourceId(
                recordLaunchWithCompleteOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(2.0)));

        // Launch again on expected resources.
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getReservedCpus(1.0, resourceId));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testIncreaseReservationScalar() throws Exception {
        // Launch for the first time with 2.0 cpus offered, 1.0 cpus required.
        String resourceId = getFirstResourceId(
                recordLaunchWithCompleteOfferedResources(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0),
                        ResourceTestUtils.getUnreservedCpus(2.0)));

        // Launch again with 1.0 cpus reserved, 1.0 cpus unreserved, and 2.0 cpus required.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        Resource offeredResource = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpus(1.0);

        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(offeredResource, unreservedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(reserveResource));

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
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
        String resourceId = getFirstResourceId(
                recordLaunchWithCompleteOfferedResources(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0, preReservedRole),
                        preReservedRole,
                        ResourceTestUtils.getUnreservedCpus(2.0, preReservedRole)));

        // Launch again with 1.0 cpus reserved, 1.0 cpus unreserved, and 2.0 cpus required.
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getCpuRequirement(2.0, preReservedRole);
        Resource offeredResource = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpus(1.0, preReservedRole);

        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(offeredResource, unreservedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(reserveResource));

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testDecreaseReservationScalar() throws Exception {
        // Launch for the first time.
        Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                PodInstanceRequirementTestUtils.getCpuRequirement(2.0),
                ResourceTestUtils.getUnreservedCpus(2.0))
                .get(0);
        String resourceId = getResourceId(reserveResource);
        Collection<Resource> offeredResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());

        // Launch again with fewer resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource offeredResource = ResourceTestUtils.getReservedCpus(2.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpus(1.0);
        offeredResources.addAll(Arrays.asList(offeredResource, unreservedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredResources)));
        Assert.assertEquals(2, recommendations.size());

        // Validate UNRESERVE Operation
        Operation unreserveOperation = recommendations.get(0).getOperation();
        Resource unreserveResource = unreserveOperation.getUnreserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOperation.getType());
        Assert.assertEquals(1.0, unreserveResource.getScalar().getValue(), 0.0);
        validateRole(unreserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(unreserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(unreserveResource));

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
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
        Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                podInstanceRequirement,
                ResourceTestUtils.getUnreservedCpus(2.0))
                .get(0);
        String resourceId = getResourceId(reserveResource);

        Resource offeredResource = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testLaunchAttributesEmbedded() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        // Launch for the first time.
        String resourceId = getFirstResourceId(
                recordLaunchWithCompleteOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(2.0)));

        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getReservedCpus(1.0, resourceId));

        Offer.Builder offerBuilder = OfferTestUtils.getOffer(expectedResources).toBuilder();
        Attribute.Builder attrBuilder =
                offerBuilder.addAttributesBuilder().setName("rack").setType(Value.Type.TEXT);
        attrBuilder.getTextBuilder().setValue("foo");
        attrBuilder = offerBuilder.addAttributesBuilder().setName("diskspeed").setType(Value.Type.SCALAR);
        attrBuilder.getScalarBuilder().setValue(1234.5678);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(offerBuilder.build()));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());

        // Validate that TaskInfo has embedded the Attributes from the selected offer:
        TaskInfo launchTask = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        Assert.assertEquals(
                Arrays.asList("rack:foo", "diskspeed:1234.568"),
                new TaskLabelReader(launchTask).getOfferAttributeStrings());
        Resource launchResource = launchTask.getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Resource offeredResource = ResourceTestUtils.getUnreservedCpus(3.0);

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

        PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
                .addTask(taskSpecA)
                .addTask(taskSpecB)
                .count(1)
                .type(TestConstants.POD_TYPE)
                .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("taskA", "taskB"))
                        .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredResource)));

        Assert.assertEquals(7, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Operation launchOp0 = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOp0.getType());
        Operation launchOp1 = recommendations.get(6).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOp1.getType());
        Protos.ExecutorID launch0ExecutorId = launchOp0.getLaunchGroup().getTaskGroup()
                .getTasks(0).getExecutor().getExecutorId();
        Protos.ExecutorID launch1ExecutorId = launchOp1.getLaunchGroup().getTaskGroup()
                .getTasks(0).getExecutor().getExecutorId();
        Assert.assertEquals(launch0ExecutorId, launch1ExecutorId);
    }

    @Test
    public void testLaunchNotOnFirstOffer() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource insufficientOffer = ResourceTestUtils.getUnreservedMem(2.0);
        Resource sufficientOffer = ResourceTestUtils.getUnreservedCpus(2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(
                        OfferTestUtils.getCompleteOffer(insufficientOffer),
                        OfferTestUtils.getCompleteOffer(sufficientOffer)));
        Assert.assertEquals(5, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
    }

    @Test
    public void testLaunchSequencedTasksInPod() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("resource-set-seq.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        // Launch Task with ONCE goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 9, recommendations.size());

        // Validate node task operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        LaunchOfferRecommendation launchOfferRecommendation = (LaunchOfferRecommendation) recommendations.get(4);
        operation = launchOfferRecommendation.getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());
        Assert.assertEquals("name-0-backup", operation.getLaunchGroup().getTaskGroup().getTasks(0).getName());
        Assert.assertFalse(launchOfferRecommendation.shouldLaunch());

        // Validate format task operations
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(6).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(7).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        launchOfferRecommendation = (LaunchOfferRecommendation) recommendations.get(8);
        operation = launchOfferRecommendation.getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());
        Assert.assertEquals("name-0-format", operation.getLaunchGroup().getTaskGroup().getTasks(0).getName());
        Assert.assertTrue(launchOfferRecommendation.shouldLaunch());

        recordOperations(recommendations);

        // Launch Task with RUNNING goal state, later.
        podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // Providing sufficient, but unreserved resources should result in no operations.
        Assert.assertEquals(0, recommendations.size());

        Resource cpuResource = operation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);
        Resource diskResource = operation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(1);
        String cpuResourceId = ResourceTestUtils.getResourceId(cpuResource);
        String diskResourceId = ResourceTestUtils.getResourceId(diskResource);
        String persistenceId = ResourceTestUtils.getPersistenceId(diskResource);
        ExecutorInfo executorInfo = stateStore.fetchTasks().iterator().next().getExecutor();
        Collection<Resource> expectedResources = getExpectedExecutorResources(executorInfo);
        expectedResources.addAll(Arrays.asList(
                ResourceTestUtils.getReservedCpus(1.0, cpuResourceId),
                ResourceTestUtils.getReservedRootVolume(50.0, diskResourceId, persistenceId)));

        Offer offer = OfferTestUtils.getOffer(expectedResources).toBuilder()
                .addExecutorIds(executorInfo.getExecutorId())
                .build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(offer));
        // Providing the expected reserved resources should result in a LAUNCH operation.
        Assert.assertEquals(1, recommendations.size());
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());
        Assert.assertEquals("name-0-node", operation.getLaunchGroup().getTaskGroup().getTasks(0).getName());
    }

    @Test
    public void testRelaunchFailedPod() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("resource-set-seq.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        // Launch Task with ONCE goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 9, recommendations.size());

        // Validate node task operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());

        // Validate format task operations
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(6).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(7).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(8).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());

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
        Assert.assertEquals(recommendations.toString(), 9, recommendations.size());

        // Validate format task operations
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(6).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());

        // Validate node task operations
        operation = recommendations.get(7).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(8).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());
    }

    @Test
    public void testTransientToPermanentFailure() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("resource-set-seq.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();

        Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        // Launch Task with RUNNING goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 9, recommendations.size());
        recordOperations(recommendations);

        // Fail the task due to a lost Agent
        TaskInfo taskInfo = stateStore.fetchTask(TaskUtils.getTaskNames(podInstance).get(0)).get();
        final Protos.TaskStatus failedStatus = TaskTestUtils.generateStatus(
                taskInfo.getTaskId(),
                Protos.TaskState.TASK_LOST);
        stateStore.storeStatus(taskInfo.getName(), failedStatus);

        // Mark the pod instance as permanently failed.
        FailureUtils.setPermanentlyFailed(stateStore, podInstance);
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));

        // A new deployment replaces the prior one above.
        Assert.assertEquals(recommendations.toString(), 9, recommendations.size());
    }

    @Test
    public void testReplaceDeployStep() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("valid-minimal-volume.yml");

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("task-name")).build();
        DeploymentStep deploymentStep = new DeploymentStep("test-step", podInstanceRequirement, stateStore);

        Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedCpus(3.0),
                ResourceTestUtils.getUnreservedMem(1024),
                ResourceTestUtils.getUnreservedDisk(500.0)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                deploymentStep.start().get(),
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 8, recommendations.size());
        Operation launchOperation = recommendations.get(7).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        recordOperations(recommendations);

        deploymentStep.updateOfferStatus(recommendations);
        Assert.assertEquals(com.mesosphere.sdk.scheduler.plan.Status.STARTING, deploymentStep.getStatus());

        // Simulate an initial failure to deploy.  Perhaps the CREATE operation failed
        deploymentStep.update(
                TaskStatus.newBuilder()
                        .setTaskId(taskInfo.getTaskId())
                        .setState(TaskState.TASK_ERROR)
                        .build());

        Assert.assertEquals(com.mesosphere.sdk.scheduler.plan.Status.PENDING, deploymentStep.getStatus());
        FailureUtils.setPermanentlyFailed(stateStore, deploymentStep.getPodInstanceRequirement().get().getPodInstance());

        Assert.assertTrue(FailureUtils.isPermanentlyFailed(stateStore.fetchTask(taskInfo.getName()).get()));

        recommendations = evaluator.evaluate(
                deploymentStep.start().get(),
                Arrays.asList(sufficientOffer));
        Assert.assertEquals(recommendations.toString(), 8, recommendations.size());

        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(7).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());
    }

    @Test
    public void testResourceRefinementSucceeds() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            ServiceSpec serviceSpec = getServiceSpec("resource-refinement.yml");
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Offer sufficientOffer = OfferTestUtils.getCompleteOffer(
                    Arrays.asList(
                            // Include executor resources.
                            ResourceTestUtils.getUnreservedCpus(0.1),
                            ResourceTestUtils.getUnreservedMem(256),
                            ResourceTestUtils.getUnreservedDisk(512),
                            ResourceTestUtils.getUnreservedCpus(3.0)).stream()
                            .map(r -> r.toBuilder()
                                    .setRole(Constants.ANY_ROLE)
                                    .addReservations(
                                            Resource.ReservationInfo.newBuilder()
                                                    .setRole(TestConstants.PRE_RESERVED_ROLE)
                                                    .setType(Resource.ReservationInfo.Type.STATIC))
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
            Assert.assertEquals(5, recommendations.size());

            Operation reserveOperation = recommendations.get(0).getOperation();
            Resource reserveResource = reserveOperation.getReserve().getResources(0);
            Assert.assertEquals(2, reserveResource.getReservationsCount());

            Resource.ReservationInfo preReservation = reserveResource.getReservations(0);
            Assert.assertEquals(Resource.ReservationInfo.Type.STATIC, preReservation.getType());
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, preReservation.getRole());
            Assert.assertFalse(preReservation.hasLabels());

            Resource.ReservationInfo dynamicReservation = reserveResource.getReservations(1);
            Assert.assertEquals(Resource.ReservationInfo.Type.DYNAMIC, dynamicReservation.getType());
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

            Offer badOffer = OfferTestUtils.getOffer(
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

    @Test
    public void testResourceRefinementFailsForDifferentPreReservation() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            ServiceSpec serviceSpec = getServiceSpec("resource-refinement.yml");
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Offer badOffer = OfferTestUtils.getOffer(
                    Arrays.asList(
                            ResourceTestUtils.getUnreservedCpus(3.0).toBuilder()
                                    .setRole(Constants.ANY_ROLE)
                                    .addReservations(
                                            Resource.ReservationInfo.newBuilder()
                                                    .setRole("different-role")
                                                    .setType(Resource.ReservationInfo.Type.STATIC))
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
    public void testGetTargetconfigRecoveryTypeNone() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0))
                        .recoveryType(RecoveryType.NONE)
                        .build();

        Assert.assertEquals(
                targetConfig,
                evaluator.getTargetConfig(podInstanceRequirement, Arrays.asList(TestConstants.TASK_INFO)));
    }

    /**
     * If recovery is taking place but no Tasks have ever been launched in this pod (logical impossibility),
     * the target configuration defined in the ConfigStore which is used to construct the OfferEvaluator should be used.
     */
    @Test
    public void testGetTargetconfigRecoveryEmptyTaskCollection() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        Assert.assertEquals(
                targetConfig,
                evaluator.getTargetConfig(podInstanceRequirement, Collections.emptyList()));
    }

    /**
     * If recovery is taking place but a Task has somehow failed to have its target config set, the
     * ConfigStore / OfferEvaluator's target config should be used.
     */
    @Test
    public void testGetTargetconfigRecoveryTypeAnyMissingLabel() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        Assert.assertEquals(
                targetConfig,
                evaluator.getTargetConfig(podInstanceRequirement, Arrays.asList(TestConstants.TASK_INFO)));
    }

    /**
     * If recovery is taking place and a target config is properly set on the task, its target config should
     * be used, not the ConfigStore / OfferEvaluator's target config.
     */
    @Test
    public void testGetTargetconfigRecoveryTypeAny() {
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0))
                        .recoveryType(RecoveryType.TRANSIENT)
                        .build();

        UUID taskConfig = UUID.randomUUID();
        TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder().setLabels(
                new TaskLabelWriter(TestConstants.TASK_INFO)
                        .setTargetConfiguration(taskConfig)
                        .toProto())
                .build();

        Assert.assertNotEquals(
                targetConfig,
                evaluator.getTargetConfig(podInstanceRequirement, Arrays.asList(taskInfo)));
        Assert.assertEquals(
                taskConfig,
                evaluator.getTargetConfig(podInstanceRequirement, Arrays.asList(taskInfo)));
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

    private void recordOperations(List<OfferRecommendation> recommendations) throws Exception {
        OperationRecorder operationRecorder = new PersistentLaunchRecorder(stateStore, serviceSpec);
        for (OfferRecommendation recommendation : recommendations) {
            operationRecorder.record(recommendation);
        }
    }

    private ServiceSpec getServiceSpec(String specFileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(specFileName).getFile());
        return DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
    }

    static void validateRole(Resource resource) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        } else {
            Assert.assertEquals(TestConstants.ROLE, resource.getRole());
        }
    }
}
