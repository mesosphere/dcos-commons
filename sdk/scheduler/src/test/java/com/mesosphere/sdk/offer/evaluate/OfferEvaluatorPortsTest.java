package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.yaml.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Offer evaluation tests concerning ports.
 */
public class OfferEvaluatorPortsTest extends OfferEvaluatorTestBase {
    private static final int TEST_PORT_A = 10000;
    private static final int TEST_PORT_B = 10001;

    @Test
    public void testLaunchExpectedPort() throws Exception {
        testExpectedPort(getSinglePortServiceSpec(), TEST_PORT_A, TEST_PORT_A);
    }

    @Test
    public void testLaunchExpectedDynamicPort() throws Exception {
        testExpectedPort(getDynamicPortServiceSpec(), TEST_PORT_A, TEST_PORT_A);
    }

    @Test
    public void testLaunchExpectedMultiplePorts() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedRanges("ports", TEST_PORT_A, TEST_PORT_B, resourceId);

        PodInstanceRequirement podInstanceRequirement = getExistingPortPodInstanceRequirement(
                desiredResource, getMultiPortFinishedServiceSpec());

        Iterator<String> it = podInstanceRequirement.getTasksToLaunch().iterator();
        it.next();
        podInstanceRequirement = PodInstanceRequirement.create(
                podInstanceRequirement.getPodInstance(), Arrays.asList(it.next()));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(desiredResource))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource =
                launchOperation
                        .getLaunch()
                        .getTaskInfosList()
                        .get(0)
                        .getResourcesList()
                        .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReserveTaskDynamicPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(TEST_PORT_A, TEST_PORT_A);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getDynamicPortPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        CommandInfo command = CommonTaskUtils.unpackTaskInfo(taskInfo).getCommand();
        Assert.assertEquals(4, command.getEnvironment().getVariablesCount());
        Environment.Variable variable = command.getEnvironment().getVariables(3);
        Assert.assertEquals(TestConstants.PORT_ENV_NAME, variable.getName());
        Assert.assertEquals(String.valueOf(TEST_PORT_A), variable.getValue());
    }

    @Test
    public void testReserveTaskMultipleDynamicPorts() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(TEST_PORT_A, TEST_PORT_B);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getMultipleDynamicPortPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource fulfilledPortResource = reserveOperation.getReserve().getResources(0);
        Assert.assertEquals(TEST_PORT_A, fulfilledPortResource.getRanges().getRange(0).getBegin());
        Assert.assertEquals(TEST_PORT_B, fulfilledPortResource.getRanges().getRange(0).getEnd());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource taskPortResource = taskInfo.getResources(0);
        Label resourceIdLabel = taskPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());
        Assert.assertEquals(
                resourceIdLabel.getValue(), fulfilledPortResource.getReservation().getLabels().getLabels(0).getValue());

        CommandInfo command = CommonTaskUtils.unpackTaskInfo(taskInfo).getCommand();
        Assert.assertEquals(5, command.getEnvironment().getVariablesCount());
        Environment.Variable variable = command.getEnvironment().getVariables(3);
        Assert.assertEquals(TestConstants.PORT_ENV_NAME, variable.getName());
        Assert.assertEquals(String.valueOf(TEST_PORT_A), variable.getValue());

        variable = command.getEnvironment().getVariables(4);
        Assert.assertEquals(TestConstants.PORT_ENV_NAME + "2", variable.getName());
        Assert.assertEquals(String.valueOf(TEST_PORT_B), variable.getValue());

        Assert.assertEquals(TEST_PORT_A, taskPortResource.getRanges().getRange(0).getBegin());
        Assert.assertEquals(TEST_PORT_B, taskPortResource.getRanges().getRange(0).getEnd());
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public void testReserveTaskNamedVIPPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(TEST_PORT_A, TEST_PORT_A);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getNamedVIPPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(TEST_PORT_A, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getNumber(), TEST_PORT_A);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":8080");
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public void testReserveTaskDynamicVIPPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(TEST_PORT_A, TEST_PORT_A);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getDynamicVIPPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(TEST_PORT_A, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getNumber(), TEST_PORT_A);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":8080");
    }

    private void testExpectedPort(RawServiceSpec rawServiceSpec, long begin, long end) throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedRanges("ports", begin, end, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPortPodInstanceRequirement(desiredResource, rawServiceSpec),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(desiredResource))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Offer.Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource =
                launchOperation
                        .getLaunch()
                        .getTaskInfosList()
                        .get(0)
                        .getResourcesList()
                        .get(0);

        Assert.assertEquals(Offer.Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    private PodInstanceRequirement getDynamicPortPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(false, getDynamicPortServiceSpec());
    }

    private PodInstanceRequirement getMultipleDynamicPortPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(false, getMultipleDynamicPortServiceSpec());
    }

    private PodInstanceRequirement getNamedVIPPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(false, getNamedVipServiceSpec());
    }

    private PodInstanceRequirement getDynamicVIPPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(false, getDynamicNamedVipServiceSpec());
    }

    private PodInstanceRequirement getExistingPortPodInstanceRequirement(
            Resource resource,
            RawServiceSpec rawServiceSpec) throws Exception {

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(false, rawServiceSpec);
        String stateStoreName = TaskSpec.getInstanceName(
                podInstanceRequirement.getPodInstance(),
                podInstanceRequirement.getPodInstance().getPod().getTasks().get(0));
        TaskInfo.Builder existingTaskInfo = offerRequirement.getTaskRequirements().iterator().next()
                .getTaskInfo()
                .toBuilder()
                .setName(stateStoreName);
        existingTaskInfo.getLabelsBuilder().setLabels(
                0, existingTaskInfo.getLabels().getLabels(0).toBuilder().setValue("pod-type"));
        existingTaskInfo.getCommandBuilder()
                .getEnvironmentBuilder()
                .addVariablesBuilder()
                .setName(TestConstants.PORT_ENV_NAME)
                .setValue(Long.toString(resource.getRanges().getRange(0).getBegin()));
        offerRequirement.updateTaskRequirement(TestConstants.TASK_NAME, existingTaskInfo.build());
        stateStore.storeTasks(Arrays.asList(existingTaskInfo.build()));

        return podInstanceRequirement;

    }

    /**
     * dynamic-port.yml
     *
     * name: "hello-world"
     * pods:
     *   pod-type:
     *     count: 1
     *     tasks:
     *     test-task:
     *       goal: RUNNING
     *       cmd: "./task-cmd"
     *       ports:
     *         test-port-name:
     *           port: 0
     *           env-key: TEST_PORT
     */
    private RawServiceSpec getDynamicPortServiceSpec() {
        WriteOnceLinkedHashMap<String, RawPort> ports = new WriteOnceLinkedHashMap<>();
        ports.put(
                "test-port-name",
                RawPort.newBuilder()
                        .port(0)
                        .envKey("TEST_PORT")
                        .build());

        return getSimpleRawServiceSpecWithPorts(ports);
    }

    /**
     * multiple-dynamic-port.yml
     *
     * name: "hello-world"
     * pods:
     *   pod-type:
     *     count: 1
     *     tasks:
     *     test-task:
     *       goal: RUNNING
     *       cmd: "./task-cmd"
     *       ports:
     *         test-port-name:
     *           port: 0
     *           env-key: TEST_PORT_1
     *         test-port-name2:
     *           port: 0
     *           env-key: TEST_PORT_2
     */
    private RawServiceSpec getMultipleDynamicPortServiceSpec() {
        WriteOnceLinkedHashMap<String, RawPort> ports = new WriteOnceLinkedHashMap<>();
        ports.put(
                "test-port-name",
                RawPort.newBuilder()
                        .port(0)
                        .envKey("TEST_PORT_1")
                        .build());
        ports.put(
                "test-port-name2",
                RawPort.newBuilder()
                        .port(0)
                        .envKey("TEST_PORT_2")
                        .build());

        return getSimpleRawServiceSpecWithPorts(ports);
    }

    /**
     * single-port.yml
     *
     * name: "hello-world"
     * pods:
     *   pod-type:
     *     count: 1
     *     tasks:
     *       test-task:
     *         goal: RUNNING
     *         cmd: "./task-cmd"
     *         ports:
     *           test-port-name:
     *             port: 10000
     */
    private RawServiceSpec getSinglePortServiceSpec() {
        WriteOnceLinkedHashMap<String, RawPort> ports = new WriteOnceLinkedHashMap<>();
        ports.put(
                "test-port-name",
                RawPort.newBuilder()
                        .port(TEST_PORT_A)
                        .build());

        return  getSimpleRawServiceSpecWithPorts(ports);
    }

    /**
     * multiple-port-with-finished.yml
     *
     * name: "hello-world"
     * pods:
     *   pod-type:
     *   count: 1
     *   resource-sets:
     *     task-resources:
     *     ports:
     *       test-port-name:
     *         port: TEST_PORT_A
     *       test-port-name2:
     *         port: TEST_PORT_B
     *   tasks:
     *     finished-task:
     *       goal: FINISHED
     *       cmd: "./finished-cmd"
     *       resource-set: task-resources
     *     test-task:
     *       goal: RUNNING
     *       cmd: "./task-cmd"
     *       resource-set: task-resources
     */
    private RawServiceSpec getMultiPortFinishedServiceSpec() {
        // ports
        WriteOnceLinkedHashMap<String, RawPort> ports = new WriteOnceLinkedHashMap<>();
        ports.put(
                "test-port-name",
                RawPort.newBuilder()
                        .port(TEST_PORT_A)
                        .build());
        ports.put(
                "test-port-name2",
                RawPort.newBuilder()
                        .port(TEST_PORT_B)
                        .build());

        // resource-set
        WriteOnceLinkedHashMap<String, RawResourceSet> resourceSets = new WriteOnceLinkedHashMap<>();
        resourceSets.put(
                "task-resources",
                RawResourceSet.newBuilder()
                        .ports(ports)
                        .build());

        // tasks
        WriteOnceLinkedHashMap<String, RawTask> tasks =  new WriteOnceLinkedHashMap<>();
        tasks.put(
                "finished-task",
                RawTask.newBuilder()
                        .goal("FINISHED")
                        .cmd("./finished-cmd")
                        .resourceSet("task-resources")
                        .build());
        tasks.put(
                "test-task",
                RawTask.newBuilder()
                        .goal("RUNNING")
                        .cmd("./test-cmd")
                        .resourceSet("task-resources")
                        .build());

        // pods
        WriteOnceLinkedHashMap<String, RawPod> pods =  new WriteOnceLinkedHashMap<>();
        pods.put(
                "pod-type",
                RawPod.newBuilder()
                        .count(1)
                        .tasks(tasks)
                        .resourceSets(resourceSets)
                        .build());

        // service
        return RawServiceSpec.newBuilder()
                .name("hello-world")
                .pods(pods)
                .build();
    }

    /**
     * named-vip.yml
     *
     * name: "hello-world"
     * pods:
     *   pod-type:
     *     count: 1
     *     tasks:
     *       test-task:
     *         goal: RUNNING
     *         cmd: "./task-cmd"
     *         ports:
     *           test-port-name:
     *             port: 10000
     *             vip:
     *               prefix: testvip
     *               port: 8080
     */
    private RawServiceSpec getNamedVipServiceSpec() {
        RawVip rawVip = RawVip.newBuilder()
                .prefix("testvip")
                .port(8080)
                .build();

        WriteOnceLinkedHashMap<String, RawPort> ports = new WriteOnceLinkedHashMap<>();
        ports.put(
                "test-port-name",
                RawPort.newBuilder()
                        .port(TEST_PORT_A)
                        .vip(rawVip)
                        .build());

        return  getSimpleRawServiceSpecWithPorts(ports);
    }

    /**
     * dynamic-vip-port.yml
     *
     * name: "hello-world"
     * pods:
     *   pod-type:
     *     count: 1
     *     tasks:
     *       test-task:
     *         goal: RUNNING
     *         cmd: "./task-cmd"
     *         ports:
     *           test-port-name:
     *             port: 0
     *             env-key: TEST_PORT
     *             vip:
     *               prefix: testvip
     *               port: 8080
     */
    private RawServiceSpec getDynamicNamedVipServiceSpec() {
        RawVip rawVip = RawVip.newBuilder()
                .prefix("testvip")
                .port(8080)
                .build();

        WriteOnceLinkedHashMap<String, RawPort> ports = new WriteOnceLinkedHashMap<>();
        ports.put(
                "test-port-name",
                RawPort.newBuilder()
                        .port(0)
                        .envKey("TEST_PORT")
                        .vip(rawVip)
                        .build());

        return  getSimpleRawServiceSpecWithPorts(ports);
    }

    protected  RawServiceSpec getSimpleRawServiceSpecWithPorts(WriteOnceLinkedHashMap<String, RawPort> ports) {
        return getSimpleRawServiceSpec(
                "test-task",
                RawTask.newBuilder()
                        .goal("RUNNING")
                        .cmd("./task-cmd")
                        .ports(ports)
                        .build());
    }
}
