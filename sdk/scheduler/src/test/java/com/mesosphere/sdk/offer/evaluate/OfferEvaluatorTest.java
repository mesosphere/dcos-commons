package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.yaml.*;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.testutils.OfferTestUtils.getOffer;

@SuppressWarnings("PMD")
public class OfferEvaluatorTest extends OfferEvaluatorTestBase {

    @Test
    public void testAvoidAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(
                        desiredCpu, Arrays.asList(TestConstants.AGENT_ID.getValue()), Collections.emptyList(), false),
                Arrays.asList(getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                getPodInstanceRequirement(
                        desiredCpu, Arrays.asList("some-random-agent"), Collections.emptyList(), false),
                Arrays.asList(getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testColocateAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(
                        desiredCpu, Collections.emptyList(), Arrays.asList("some-random-agent"), false),
                Arrays.asList(getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                getPodInstanceRequirement(
                        desiredCpu, Collections.emptyList(), Arrays.asList(TestConstants.AGENT_ID.getValue()), false),
                Arrays.asList(getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 3.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getMultipleTaskPodInstanceRequirement(),
                Arrays.asList(getOffer(null, Arrays.asList(offeredResource))));

        Assert.assertEquals(4, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Operation launchOp0 = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp0.getType());
        Operation launchOp1 = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp1.getType());
        Protos.ExecutorID launch0ExecutorId = launchOp0.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
        Protos.ExecutorID launch1ExecutorId = launchOp1.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
        Assert.assertEquals(launch0ExecutorId, launch1ExecutorId);
    }

    @Test
    public void testLaunchNotOnFirstOffer() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource insufficientOffer = ResourceUtils.getUnreservedScalar("mem", 2.0);
        Resource sufficientOffer = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(desiredResource),
                Arrays.asList(
                        getOffer(insufficientOffer),
                        getOffer(sufficientOffer)));
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
        RawServiceSpec rawServiceSpec = getResourceSetSeqSpec();
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.create(podInstance, Arrays.asList("format"));

        Offer sufficientOffer = getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 3.0),
                ResourceUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 4, recommendations.size());

        // Validate RESERVE Operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());

        // Validate LAUNCH Operations
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        recordOperations(recommendations, sufficientOffer);

        // Launch Task with RUNNING goal state, later.
        podInstanceRequirement = PodInstanceRequirement.create(podInstance, Arrays.asList("node"));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // Providing sufficient, but unreserved resources should result in no operations.
        Assert.assertEquals(0, recommendations.size());

        List<String> resourceIds = offerRequirementProvider.getExistingOfferRequirement(podInstance, Arrays.asList("node"))
                .getTaskRequirements().stream()
                .flatMap(taskRequirement -> taskRequirement.getResourceRequirements().stream())
                .map(resourceRequirement -> resourceRequirement.getResourceId())
                .collect(Collectors.toList());
        Assert.assertEquals(resourceIds.toString(), 1, resourceIds.size());

        Offer expectedOffer = getOffer(Arrays.asList(
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceIds.get(0))));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(expectedOffer));
        // Providing the expected reserved resources should result in a LAUNCH operation.
        Assert.assertEquals(1, recommendations.size());
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
    }

    @Test
    public void testRelaunchFailedPod() throws Exception {
        RawServiceSpec rawServiceSpec = getResourceSetSeqSpec();
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.create(podInstance, Arrays.asList("finished1"));

        Offer sufficientOffer = getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 3.0),
                ResourceUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 4, recommendations.size());

        // Validate RESERVE Operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());

        // Validate LAUNCH Operations
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        recordOperations(recommendations, sufficientOffer);

        // Attempt to launch task again as non-failed.
        podInstanceRequirement = PodInstanceRequirement.create(podInstance, Arrays.asList("node"));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // The pod is running fine according to the state store, so no new deployment is issued.
        Assert.assertEquals(recommendations.toString(), 0, recommendations.size());

        // Now the same operation except with the task flagged as having permanently failed.
        podInstanceRequirement = PodInstanceRequirement.createPermanentReplacement(podInstance, Arrays.asList("node"));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // A new deployment replaces the prior one above.
        Assert.assertEquals(recommendations.toString(), 4, recommendations.size());

        // Validate RESERVE Operations
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());

        // Validate LAUNCH Operations
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
    }

    @Test
    public void testLaunchAttributesEmbedded() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Offer.Builder offerBuilder = getOffer(desiredResource).toBuilder();
        Attribute.Builder attrBuilder =
                offerBuilder.addAttributesBuilder().setName("rack").setType(Value.Type.TEXT);
        attrBuilder.getTextBuilder().setValue("foo");
        attrBuilder = offerBuilder.addAttributesBuilder().setName("diskspeed").setType(Value.Type.SCALAR);
        attrBuilder.getScalarBuilder().setValue(1234.5678);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(desiredResource, false),
                Arrays.asList(offerBuilder.build()));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        // Validate that TaskInfo has embedded the Attributes from the selected offer:
        TaskInfo launchTask = launchOperation.getLaunch().getTaskInfosList().get(0);
        Assert.assertEquals(
                Arrays.asList("rack:foo", "diskspeed:1234.568"),
                CommonTaskUtils.getOfferAttributeStrings(launchTask));
        Resource launchResource = launchTask.getResourcesList().get(0);
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }


    private PodInstanceRequirement getMultipleTaskPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(false, getMultiTaskPodSpec());
    }

    /**
     * multiple-task.yml
     *
     * name: "hello-world"
     * pods:
     *   pod-type:
     *     count: 1
     *     tasks:
     *       test-task:
     *         goal: RUNNING
     *         cmd: "./task-cmd"
     *         cpus: 1.0
     *       test-task2:
     *         goal: RUNNING
     *         cmd: "./task-cmd"
     *         cpus: 2.0
     */
    private RawServiceSpec getMultiTaskPodSpec() {
        Map<String, RawTask> taskMap = new HashMap<>();
        taskMap.put(
                "test-task",
                RawTask.newBuilder()
                        .goal("RUNNING")
                        .cmd("./task-cmd")
                        .cpus(1.0)
                        .build());
        taskMap.put(
                "test-task2",
                RawTask.newBuilder()
                        .goal("RUNNING")
                        .cmd("./task-cmd")
                        .cpus(2.0)
                        .build());

        return getSimpleRawServiceSpec(taskMap);
    }

    /**
     * resource-set-seq.yml
     *
     * name: "test"
     * pods:
     *   pod-type:
     *     count: 2
     *     resource-sets:
     *       main-resources:
     *         cpus: 1.0
     *       sidecar-resources:
     *         cpus: 2.0
     *     tasks:
     *       node:
     *         goal: RUNNING
     *         cmd: "./node"
     *         resource-set: main-resources
     *       finished1:
     *         goal: FINISHED
     *         cmd: "./finished1"
     *         resource-set: main-resources
     *       finished2:
     *         goal: FINISHED
     *         cmd: "./finished2"
     *         resource-set: main-resources
     *       sidecar:
     *         goal: FINISHED
     *         cmd: "./sidecar"
     *         resource-set: sidecar-resources
     */
    private RawServiceSpec getResourceSetSeqSpec() {
        // resource-sets
        WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets = new WriteOnceLinkedHashMap<>();
        resourceSets.put(
                "main-resources",
                RawResourceSet.newBuilder()
                        .cpus(1.0)
                        .build());
        resourceSets.put(
                "sidecar-resources",
                RawResourceSet.newBuilder()
                        .cpus(2.0)
                        .build());

        // tasks
        WriteOnceLinkedHashMap<String, RawTask> taskMap =  new WriteOnceLinkedHashMap<>();
        taskMap.put(
                "node",
                RawTask.newBuilder()
                        .goal("RUNNING")
                        .cmd("./node")
                        .resourceSet("main-resources")
                        .build());
        taskMap.put(
                "finished1",
                RawTask.newBuilder()
                        .goal("FINISHED")
                        .cmd("./finished1")
                        .resourceSet("main-resources")
                        .build());
        taskMap.put(
                "finished2",
                RawTask.newBuilder()
                        .goal("FINISHED")
                        .cmd("./finished2")
                        .resourceSet("main-resources")
                        .build());
        taskMap.put(
                "sidecar",
                RawTask.newBuilder()
                        .goal("FINISHED")
                        .cmd("./sidecar")
                        .resourceSet("sidecar-resources")
                        .build());

        // pods
        WriteOnceLinkedHashMap<String, RawPod> pods =  new WriteOnceLinkedHashMap<>();
        pods.put(
                "pod-type",
                RawPod.newBuilder()
                        .count(2)
                        .resourceSets(resourceSets)
                        .tasks(taskMap)
                        .build());

        return RawServiceSpec.newBuilder()
                .name("test")
                .pods(pods)
                .build();
    }

    private void recordOperations(List<OfferRecommendation> recommendations, Offer offer) throws Exception {
        for (OfferRecommendation recommendation : recommendations) {
            operationRecorder.record(recommendation.getOperation(), offer);
        }
    }

}
