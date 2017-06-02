package com.mesosphere.sdk.offer.evaluate;

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
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
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
import java.util.Arrays;
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
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceCollectionUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(Constants.ANY_ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.ROLE, ResourceCollectionUtils.getRole(reserveResource).get());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(36, getResourceId(reserveResource).length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getResourceId(reserveResource), getResourceId(launchResource));
        String executorId = launchOperation
                .getLaunch()
                .getTaskInfos(0)
                .getExecutor()
                .getExecutorId()
                .getValue();

        String prefix = TestConstants.POD_TYPE + CommonIdUtils.NAME_ID_DELIM;
        Assert.assertTrue(executorId.startsWith(prefix));
        Assert.assertEquals(prefix.length() + UUID.randomUUID().toString().length(), executorId.length());
    }

    @Test
    public void testRelaunchExpectedScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        // Launch for the first time.
        String resourceId = getFirstResourceId(
                recordLaunchWithOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedScalar("cpus", 2.0)));

        // Launch again on expected resources.
        Resource expectedScalar = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(expectedScalar))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testIncreaseReservationScalar() throws Exception {
        // Launch for the first time.
        String resourceId = getFirstResourceId(
                recordLaunchWithOfferedResources(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0),
                        ResourceTestUtils.getUnreservedScalar("cpus", 2.0)));

        // Launch again with more resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource, unreservedResource))));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceCollectionUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(Constants.ANY_ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.ROLE, ResourceCollectionUtils.getRole(reserveResource).get());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(reserveResource));

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testDecreaseReservationScalar() throws Exception {
        // Launch for the first time.
        Resource reserveResource = recordLaunchWithOfferedResources(
                PodInstanceRequirementTestUtils.getCpuRequirement(2.0),
                ResourceTestUtils.getUnreservedScalar("cpus", 2.0))
                .get(0);
        String resourceId = getResourceId(reserveResource);

        // Launch again with fewer resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource, unreservedResource))));
        Assert.assertEquals(2, recommendations.size());

        // Validate UNRESERVE Operation
        Operation unreserveOperation = recommendations.get(0).getOperation();
        Resource unreserveResource = unreserveOperation.getUnreserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceCollectionUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOperation.getType());
        Assert.assertEquals(1.0, unreserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(Constants.ANY_ROLE, unreserveResource.getRole());
        Assert.assertEquals(TestConstants.ROLE, ResourceCollectionUtils.getRole(unreserveResource).get());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(unreserveResource));

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testFailIncreaseReservationScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        Resource reserveResource = recordLaunchWithOfferedResources(
                podInstanceRequirement,
                ResourceTestUtils.getUnreservedScalar("cpus", 2.0))
                .get(0);
        String resourceId = getResourceId(reserveResource);

        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testLaunchAttributesEmbedded() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        // Launch for the first time.
        String resourceId = getFirstResourceId(
                recordLaunchWithOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedScalar("cpus", 2.0)));
        Resource expectedResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        Offer.Builder offerBuilder = OfferTestUtils.getOffer(expectedResource).toBuilder();
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
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        // Validate that TaskInfo has embedded the Attributes from the selected offer:
        TaskInfo launchTask = launchOperation.getLaunch().getTaskInfos(0);
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

        CommandSpec commandSpec = DefaultCommandSpec.newBuilder(TestConstants.POD_TYPE)
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
                Arrays.asList(OfferTestUtils.getOffer(null, Arrays.asList(offeredResource))));

        Assert.assertEquals(4, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Operation launchOp0 = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp0.getType());
        Operation launchOp1 = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp1.getType());
        Protos.ExecutorID launch0ExecutorId = launchOp0.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
        Protos.ExecutorID launch1ExecutorId = launchOp1.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
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
                        OfferTestUtils.getOffer(insufficientOffer),
                        OfferTestUtils.getOffer(sufficientOffer)));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    }

    @Test
    public void testLaunchSequencedTasksInPod() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate node task operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        LaunchOfferRecommendation launchOfferRecommendation = (LaunchOfferRecommendation) recommendations.get(1);
        operation = launchOfferRecommendation.getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        Assert.assertEquals("name-0-backup", operation.getLaunch().getTaskInfos(0).getName());
        Assert.assertFalse(launchOfferRecommendation.shouldLaunch());

        // Validate format task operations
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        launchOfferRecommendation = (LaunchOfferRecommendation) recommendations.get(5);
        operation = launchOfferRecommendation.getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        Assert.assertEquals("name-0-format", operation.getLaunch().getTaskInfos(0).getName());
        Assert.assertTrue(launchOfferRecommendation.shouldLaunch());

        recordOperations(recommendations);

        // Launch Task with RUNNING goal state, later.
        podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // Providing sufficient, but unreserved resources should result in no operations.
        Assert.assertEquals(0, recommendations.size());

        Resource cpuResource = operation.getLaunch().getTaskInfos(0).getResources(0);
        Resource diskResource = operation.getLaunch().getTaskInfos(0).getResources(1);
        String cpuResourceId = ResourceTestUtils.getResourceId(cpuResource);
        String diskResourceId = ResourceTestUtils.getResourceId(diskResource);
        String persistenceId = ResourceTestUtils.getPersistenceId(diskResource);

        Offer expectedOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, cpuResourceId),
                ResourceTestUtils.getExpectedRootVolume(50.0, diskResourceId, persistenceId)));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(expectedOffer));
        // Providing the expected reserved resources should result in a LAUNCH operation.
        Assert.assertEquals(1, recommendations.size());
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        Assert.assertEquals("name-0-node", operation.getLaunch().getTaskInfos(0).getName());
    }

    @Test
    public void testRelaunchFailedPod() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate node task operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        // Validate format task operations
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

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
        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate format task operations
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        // Validate node task operations
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
    }

    @Test
    public void testReplaceDeployStep() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal-volume.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("task-name")).build();
        DeploymentStep deploymentStep = new DeploymentStep(
                "test-step",
                Status.PENDING,
                podInstanceRequirement,
                Collections.emptyList());

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("mem", 1024),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                deploymentStep.start().get(),
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 5, recommendations.size());
        Operation launchOperation = recommendations.get(4).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
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
        Assert.assertEquals(recommendations.toString(), 5, recommendations.size());

        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

    }

    @Test
    public void testResourceRefinementSucceeds() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-refinement.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);
        Assert.assertEquals("base-role", serviceSpec.getPods().get(0).getPreReservedRole());

        Offer sufficientOffer = OfferTestUtils.getOffer(
                Arrays.asList(
                        ResourceTestUtils.getUnreservedScalar("cpus", 3.0).toBuilder()
                                .setRole("base-role")
                                .build()));

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        List<String> tasksToLaunch = TaskUtils.getTaskNames(podInstance);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch)
                .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testResourceRefinementFails() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-refinement.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);
        Assert.assertEquals("base-role", serviceSpec.getPods().get(0).getPreReservedRole());

        Offer sufficientOffer = OfferTestUtils.getOffer(
                Arrays.asList(ResourceTestUtils.getUnreservedScalar("cpus", 3.0)));

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        List<String> tasksToLaunch = TaskUtils.getTaskNames(podInstance);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch)
                .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(0, recommendations.size());
    }

    private void recordOperations(List<OfferRecommendation> recommendations) throws Exception {
        OperationRecorder operationRecorder = new PersistentLaunchRecorder(stateStore, serviceSpec);
        for (OfferRecommendation recommendation : recommendations) {
            operationRecorder.record(recommendation);
        }
    }
}
