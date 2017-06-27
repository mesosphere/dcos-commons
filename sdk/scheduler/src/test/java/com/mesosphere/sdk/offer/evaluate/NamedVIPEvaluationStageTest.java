package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

/**
 * Tests for {@link NamedVIPEvaluationStage}.
 */
public class NamedVIPEvaluationStageTest extends DefaultCapabilitiesTestSuite {

    @Test
    public void testDiscoveryInfoPopulated() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        boolean onOverlay = false;
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(10000, Collections.emptyList(), onOverlay);

        // Evaluate stage
        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(10000, Optional.empty(), onOverlay);
        EvaluationOutcome outcome = vipEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        String expectedName = TestConstants.POD_TYPE + "-0-" + TestConstants.TASK_NAME;
        String observedName = discoveryInfo.getName();
        Assert.assertEquals(expectedName, observedName);
        Assert.assertEquals(DiscoveryInfo.Visibility.CLUSTER, discoveryInfo.getVisibility());

        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), 10000);
        Assert.assertEquals(port.getProtocol(), "sctp");
        Assert.assertEquals(1, port.getLabels().getLabelsCount());
        Protos.Label vipLabel = port.getLabels().getLabels(0);
        Assert.assertEquals("pod-type-0-test-task-name", discoveryInfo.getName());
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");
        //TODO(arand) check that second discovery label is NOT added
    }

    @Test
    public void testDiscoveryInfoWhenOnOverlay() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        boolean onOverlay = true;

        Integer containerPort = 80;  // non-offered port

        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(containerPort, Collections.emptyList(), onOverlay);

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(containerPort, Optional.empty(), onOverlay);

        EvaluationOutcome outcome = vipEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        String expectedName = TestConstants.POD_TYPE + "-0-" + TestConstants.TASK_NAME;
        String observedName = discoveryInfo.getName();
        Assert.assertEquals(expectedName, observedName);
        Assert.assertEquals(DiscoveryInfo.Visibility.CLUSTER, discoveryInfo.getVisibility());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Assert.assertEquals(0, taskBuilder.getResourcesCount());
        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), containerPort.longValue());
        Assert.assertEquals(port.getProtocol(), "sctp");

        Assert.assertEquals(2, port.getLabels().getLabelsCount());
        Protos.Label vipLabel = port.getLabels().getLabels(0);

        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");

        vipLabel = port.getLabels().getLabels(1);
        Assert.assertEquals(Constants.VIP_OVERLAY_FLAG_KEY, vipLabel.getKey());
        Assert.assertEquals(Constants.VIP_OVERLAY_FLAG_VALUE, vipLabel.getValue());
    }

    @Test
    public void testDiscoveryInfoWhenOnOverlayWithDynamicPort() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        boolean onOverlay = true;

        Integer containerPort = 0;  // non-offered port

        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(containerPort, Collections.emptyList(), onOverlay);

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(containerPort, Optional.empty(), onOverlay);

        EvaluationOutcome outcome = vipEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        String expectedName = TestConstants.POD_TYPE + "-0-" + TestConstants.TASK_NAME;
        String observedName = discoveryInfo.getName();
        Assert.assertEquals(expectedName, observedName);
        Assert.assertEquals(DiscoveryInfo.Visibility.CLUSTER, discoveryInfo.getVisibility());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Assert.assertEquals(0, taskBuilder.getResourcesCount());
        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START.longValue());
        Assert.assertEquals(port.getProtocol(), "sctp");

        Assert.assertEquals(2, port.getLabels().getLabelsCount());
        Protos.Label vipLabel = port.getLabels().getLabels(0);

        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");

        vipLabel = port.getLabels().getLabels(1);
        Assert.assertEquals(Constants.VIP_OVERLAY_FLAG_KEY, vipLabel.getKey());
        Assert.assertEquals(Constants.VIP_OVERLAY_FLAG_VALUE, vipLabel.getValue());
    }


    @Test
    public void testVIPIsReused() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource offeredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        boolean onOverlay = false;
        String vipLabelKey = "VIP_LABEL_KEY";
        Collection<Protos.TaskInfo> taskInfos = Arrays.asList(
                Protos.TaskInfo.newBuilder()
                        .setName("pod-type-0-test-task-name")
                        .setTaskId(TestConstants.TASK_ID)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .setCommand(
                                Protos.CommandInfo.newBuilder()
                                        .setValue("./cmd")
                                        .setEnvironment(
                                                Protos.Environment.newBuilder()
                                                        .addVariables(
                                                                Protos.Environment.Variable.newBuilder()
                                                                        .setName("TEST_PORT_NAME_VIP_0")
                                                                        .setValue("10000"))))
                        .build());

        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(10000, taskInfos, onOverlay);
        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(10000, Optional.of(resourceId), onOverlay);

        EvaluationOutcome outcome = vipEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(1, discoveryInfo.getPorts().getPortsList().size());
        Assert.assertEquals(1, discoveryInfo.getPorts().getPorts(0).getLabels().getLabelsList().size());
    }

    @Test
    public void testUpdatePortWithVipLabel() throws InvalidRequirementException {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 20000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        boolean onOverlay = false;
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(10000, Collections.emptyList(), onOverlay);
        // Evaluate stage
        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(10000, Optional.empty(), onOverlay);
        EvaluationOutcome outcome = vipEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertTrue(discoveryInfo.getPorts().getPortsCount() == 1);
        Protos.Port portInfo = discoveryInfo.getPorts().getPorts(0);
        Assert.assertTrue(portInfo.getName().equals("test-port"));
        Assert.assertTrue(portInfo.getNumber() == 10000);
        Assert.assertTrue(portInfo.getLabels().getLabels(0).getKey().startsWith("VIP_"));
        Assert.assertTrue(portInfo.getLabels().getLabels(0).getValue().equals("test-vip:80"));
        NamedVIPEvaluationStage vipEvaluationStage2 = getEvaluationStage(20000, Optional.empty(), onOverlay);
        outcome = vipEvaluationStage2.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertTrue(discoveryInfo.getPorts().getPortsCount() == 1);
        portInfo = discoveryInfo.getPorts().getPorts(0);
        Assert.assertTrue(portInfo.getName().equals("test-port"));
        Assert.assertTrue(portInfo.getNumber() == 20000);
        Assert.assertTrue(portInfo.getLabels().getLabels(0).getKey().startsWith("VIP_"));
        Assert.assertTrue(portInfo.getLabels().getLabels(0).getValue().equals("test-vip:80"));
    }

    @Test
    public void testPortNumberIsUpdated() throws InvalidRequirementException {
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedPorts(8000, 8000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        String vipLabelKey = "VIP_LABEL_KEY";
        boolean onOverlay = false;
        Collection<Protos.TaskInfo> taskInfos = Arrays.asList(
                Protos.TaskInfo.newBuilder()
                        .setName("pod-type-0-test-task-name")
                        .setTaskId(TestConstants.TASK_ID)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .setCommand(
                                Protos.CommandInfo.newBuilder()
                                        .setValue("./cmd")
                                        .setEnvironment(
                                                Protos.Environment.newBuilder()
                                                        .addVariables(
                                                                Protos.Environment.Variable.newBuilder()
                                                                        .setName("TEST_PORT_NAME_VIP_0")
                                                                        .setValue("10000"))))
                        .build());

        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(8000, taskInfos, onOverlay);

        // Update the resource to have a different port, so that the TaskInfo's DiscoveryInfo mirrors the case where
        // a new port has been requested but we want to reuse the old VIP definition.

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(8000, Optional.empty(), onOverlay);
        EvaluationOutcome outcome = vipEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(1, discoveryInfo.getPorts().getPortsList().size());
        Assert.assertEquals(1, discoveryInfo.getPorts().getPorts(0).getLabels().getLabelsList().size());
        Assert.assertEquals(8000, discoveryInfo.getPorts().getPorts(0).getNumber());
    }

    private NamedVIPEvaluationStage getEvaluationStage(int taskPort, Optional<String> resourceId, boolean onOverlay) {
        return new NamedVIPEvaluationStage(
                getNamedVIPSpec(taskPort, onOverlay),
                TestConstants.TASK_NAME,
                resourceId,
                "test-port",
                true);
    }

    private NamedVIPSpec getNamedVIPSpec(int taskPort, boolean onOverlay) {
        Protos.Value.Builder valueBuilder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        valueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(taskPort)
                .setEnd(taskPort);

        List<String> networkNames = onOverlay ? new ArrayList<>(Arrays.asList(DcosConstants.DEFAULT_OVERLAY_NETWORK)):
                Collections.emptyList();

        return new NamedVIPSpec(
                valueBuilder.build(),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.PORT_ENV_NAME + "_VIP_" + taskPort,
                TestConstants.VIP_NAME + "-" + taskPort,
                "sctp",
                DiscoveryInfo.Visibility.EXTERNAL,
                "test-vip",
                80,
                networkNames);
    }

    private PodInstanceRequirement getPodInstanceRequirement(int taskPort, boolean onOverlay) {
        // Build Pod
        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE,Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .id("resourceSet")
                .cpus(1.0)
                .addResource(getNamedVIPSpec(taskPort, onOverlay))
                .build();
        CommandSpec commandSpec = DefaultCommandSpec.newBuilder(Collections.emptyMap())
                .value("./cmd")
                .build();
        TaskSpec taskSpec = DefaultTaskSpec.newBuilder()
                .name(TestConstants.TASK_NAME)
                .commandSpec(commandSpec)
                .goalState(GoalState.RUNNING)
                .resourceSet(resourceSet)
                .build();
        PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
                .addTask(taskSpec)
                .count(1)
                .type(TestConstants.POD_TYPE)
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        return PodInstanceRequirement.newBuilder(podInstance, Arrays.asList(TestConstants.TASK_NAME)).build();
    }

    private PodInfoBuilder getPodInfoBuilder(int taskPort, Collection<Protos.TaskInfo> taskInfos, boolean onOverlay)
            throws InvalidRequirementException {
        return new PodInfoBuilder(
                getPodInstanceRequirement(taskPort, onOverlay),
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                taskInfos,
                TestConstants.FRAMEWORK_ID,
                true);
    }
}
