package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.ResourceRefinementCapabilityContext;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.DeploymentStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.PersistentLaunchRecorder;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test; import org.mockito.Mock;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class OfferEvaluatorTest extends OfferEvaluatorTestBase {
    @Mock ServiceSpec serviceSpec;

    @Test
    public void testReserveLaunchScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource offeredResource = ResourceTestUtils.getUnreservedScalar("cpus", 2.0);

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

        Resource expectedExecutorCpu = ResourceTestUtils.getExpectedScalar("cpus", 0.1, executorCpuId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 32, executorMemId);
        Resource expectedExecutorDisk = ResourceTestUtils.getExpectedScalar("disk", 256, executorDiskId);

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
                        ResourceTestUtils.getUnreservedScalar("cpus", 2.0)));

        // Launch again on expected resources.
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId));

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
        // Launch for the first time.
        String resourceId = getFirstResourceId(
                recordLaunchWithCompleteOfferedResources(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0),
                        ResourceTestUtils.getUnreservedScalar("cpus", 2.0)));

        // Launch again with more resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

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
    public void testDecreaseReservationScalar() throws Exception {
        // Launch for the first time.
        Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                PodInstanceRequirementTestUtils.getCpuRequirement(2.0),
                ResourceTestUtils.getUnreservedScalar("cpus", 2.0))
                .get(0);
        String resourceId = getResourceId(reserveResource);
        Collection<Resource> offeredResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());

        // Launch again with fewer resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);
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
                ResourceTestUtils.getUnreservedScalar("cpus", 2.0))
                .get(0);
        String resourceId = getResourceId(reserveResource);

        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
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
                        ResourceTestUtils.getUnreservedScalar("cpus", 2.0)));

        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId));

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
                new SchedulerLabelReader(launchTask).getOfferAttributeStrings());
        Resource launchResource = launchTask.getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Resource offeredResource = ResourceTestUtils.getUnreservedScalar("cpus", 3.0);

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
        Resource insufficientOffer = ResourceTestUtils.getUnreservedScalar("mem", 2.0);
        Resource sufficientOffer = ResourceTestUtils.getUnreservedScalar("cpus", 2.0);

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
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
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
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, cpuResourceId),
                ResourceTestUtils.getExpectedRootVolume(50.0, diskResourceId, persistenceId)));

        Offer offer = OfferTestUtils.getOffer(expectedResources);
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(offer));
        // Providing the expected reserved resources should result in a LAUNCH operation.
        Assert.assertEquals(1, recommendations.size());
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, operation.getType());
        Assert.assertEquals("name-0-node", operation.getLaunchGroup().getTaskGroup().getTasks(0).getName());
    }

    @Test
    public void testRelaunchFailedPod() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
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
    public void testReplaceDeployStep() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal-volume.yml").getFile());
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("task-name")).build();
        DeploymentStep deploymentStep = new DeploymentStep(
                "test-step",
                Status.PENDING,
                podInstanceRequirement,
                Collections.emptyList());

        Offer sufficientOffer = OfferTestUtils.getCompleteOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("mem", 1024),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                deploymentStep.start().get(),
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 8, recommendations.size());
        Operation launchOperation = recommendations.get(7).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        recordOperations(recommendations);

        deploymentStep.updateOfferStatus(recommendations);
        Assert.assertEquals(Status.STARTING, deploymentStep.getStatus());

        // Simulate an initial failure to deploy.  Perhaps the CREATE operation failed
        deploymentStep.update(
                TaskStatus.newBuilder()
                        .setTaskId(taskInfo.getTaskId())
                        .setState(TaskState.TASK_ERROR)
                        .build());

        Assert.assertEquals(Status.PENDING, deploymentStep.getStatus());
        FailureUtils.markFailed(
                deploymentStep.getPodInstanceRequirement().get().getPodInstance(),
                stateStore);

        Assert.assertTrue(FailureUtils.isLabeledAsFailed(stateStore.fetchTask(taskInfo.getName()).get()));

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
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("resource-refinement.yml").getFile());
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
            DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Offer sufficientOffer = OfferTestUtils.getCompleteOffer(
                    Arrays.asList(
                            ResourceTestUtils.getUnreservedScalar("cpus", 3.0).toBuilder()
                                    .setRole(Constants.ANY_ROLE)
                                    .addReservations(
                                            Resource.ReservationInfo.newBuilder()
                                                    .setRole(TestConstants.PRE_RESERVED_ROLE)
                                                    .setType(Resource.ReservationInfo.Type.STATIC))
                                    .build()));

            PodSpec podSpec = serviceSpec.getPods().get(0);
            PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
            List<String> tasksToLaunch = TaskUtils.getTaskNames(podInstance);
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
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("resource-refinement.yml").getFile());
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
            DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Offer badOffer = OfferTestUtils.getOffer(
                    Arrays.asList(ResourceTestUtils.getUnreservedScalar("cpus", 3.0)));

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
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("resource-refinement.yml").getFile());
            RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).build();
            DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, flags).build();
            Assert.assertEquals(TestConstants.PRE_RESERVED_ROLE, serviceSpec.getPods().get(0).getPreReservedRole());

            Offer badOffer = OfferTestUtils.getOffer(
                    Arrays.asList(
                            ResourceTestUtils.getUnreservedScalar("cpus", 3.0).toBuilder()
                                    .setRole(Constants.ANY_ROLE)
                                    .addReservations(
                                            Resource.ReservationInfo.newBuilder()
                                                    .setRole("different-role")
                                                    .setType(Resource.ReservationInfo.Type.STATIC))
                                    .build()));

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

    private void recordOperations(List<OfferRecommendation> recommendations) throws Exception {
        OperationRecorder operationRecorder = new PersistentLaunchRecorder(stateStore, serviceSpec);
        for (OfferRecommendation recommendation : recommendations) {
            operationRecorder.record(recommendation);
        }
    }

    static void validateRole(Resource resource) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            Assert.assertEquals(Constants.ANY_ROLE, resource.getRole());
        } else {
            Assert.assertEquals(TestConstants.ROLE, resource.getRole());
        }
    }
}
