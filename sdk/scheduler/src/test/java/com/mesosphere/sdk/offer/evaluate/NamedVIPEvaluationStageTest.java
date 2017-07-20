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

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnHost(10000, Collections.emptyList());

        // Evaluate stage
        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnHost(10000, Optional.empty());
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
    }

    @Test
    public void testDiscoveryInfoWhenOnOverlay() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        Integer containerPort = 80;  // non-offered port

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnOverlay(containerPort, Collections.emptyList());

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnOverlay(containerPort, Optional.empty());

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
        Assert.assertEquals(DcosConstants.VIP_OVERLAY_FLAG_KEY, vipLabel.getKey());
        Assert.assertEquals(DcosConstants.VIP_OVERLAY_FLAG_VALUE, vipLabel.getValue());
    }

    @Test
    public void testDiscoveryInfoOnBridgeNetwork() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        Integer containerPort = 10000;  // non-offered port

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnBridgeNetwork(containerPort, Collections.emptyList());

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnBridgeNetwork(containerPort, Optional.empty());

        EvaluationOutcome outcome = vipEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        String expectedName = TestConstants.POD_TYPE + "-0-" + TestConstants.TASK_NAME;
        String observedName = discoveryInfo.getName();
        Assert.assertEquals(expectedName, observedName);
        Assert.assertEquals(DiscoveryInfo.Visibility.CLUSTER, discoveryInfo.getVisibility());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Assert.assertEquals(1, taskBuilder.getResourcesCount());  // expect that bridge uses ports
        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), containerPort.longValue());
        Assert.assertEquals(port.getProtocol(), "sctp");

        Assert.assertEquals(2, port.getLabels().getLabelsCount());

        Protos.Label vipLabel = port.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");

        vipLabel = port.getLabels().getLabels(1);
        Assert.assertEquals(DcosConstants.VIP_OVERLAY_FLAG_KEY, vipLabel.getKey());
        Assert.assertEquals(DcosConstants.BRIDGE_FLAG_VALUE, vipLabel.getValue());
    }

    @Test
    public void testDiscoveryInfoWhenOnOverlayWithDynamicPort() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        Integer containerPort = 0;  // non-offered port

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnOverlay(containerPort, Collections.emptyList());

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnOverlay(containerPort, Optional.empty());

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
        Assert.assertEquals(DcosConstants.VIP_OVERLAY_FLAG_KEY, vipLabel.getKey());
        Assert.assertEquals(DcosConstants.VIP_OVERLAY_FLAG_VALUE, vipLabel.getValue());
    }

    private NamedVIPEvaluationStage getEvaluationStageOnHost(int taskPort, Optional<String> resourceId) {
        List<String> networkNames = Collections.emptyList();
        return new NamedVIPEvaluationStage(
                getNamedVIPSpec(taskPort, networkNames),
                TestConstants.TASK_NAME,
                resourceId,
                true);
    }

    private NamedVIPEvaluationStage getEvaluationStageOnOverlay(int taskPort, Optional<String> resourceId) {
        List<String> networkNames = new ArrayList<>(Arrays.asList(DcosConstants.DEFAULT_OVERLAY_NETWORK));
        return new NamedVIPEvaluationStage(
                getNamedVIPSpec(taskPort, networkNames),
                TestConstants.TASK_NAME,
                resourceId,
                true);
    }

    private NamedVIPEvaluationStage getEvaluationStageOnBridgeNetwork(int taskPort, Optional<String> resourceId) {
        List<String> networkNames = new ArrayList<>(Arrays.asList(DcosConstants.DEFAULT_BRIDGE_NETWORK));
        return new NamedVIPEvaluationStage(
                getNamedVIPSpec(taskPort, networkNames),
                TestConstants.TASK_NAME,
                resourceId,
                true);
    }

    private NamedVIPSpec getNamedVIPSpec(int taskPort, List<String> networkNames) {
        Protos.Value.Builder valueBuilder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        valueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(taskPort)
                .setEnd(taskPort);

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

    private PodInstanceRequirement getPodInstanceRequirement(int taskPort, List<String> networkNames) {
        // Build Pod
        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE,Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .id("resourceSet")
                .cpus(1.0)
                .addResource(getNamedVIPSpec(taskPort, networkNames))
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

    private PodInfoBuilder getPodInfoBuilder(int taskPort, Collection<Protos.TaskInfo> taskInfos,
                                             List<String> networkNames)
            throws InvalidRequirementException {
        return new PodInfoBuilder(
                getPodInstanceRequirement(taskPort, networkNames),
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                taskInfos,
                TestConstants.FRAMEWORK_ID,
                true);
    }

    private PodInfoBuilder getPodInfoBuilderOnOverlay(int taskPort, Collection<Protos.TaskInfo> taskInfos)
            throws InvalidRequirementException {
        return new PodInfoBuilder(
                getPodInstanceRequirement(taskPort, Arrays.asList(DcosConstants.DEFAULT_OVERLAY_NETWORK)),
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                taskInfos,
                TestConstants.FRAMEWORK_ID,
                true);
    }

    private PodInfoBuilder getPodInfoBuilderOnHost(int taskPort, Collection<Protos.TaskInfo> taskInfos)
            throws InvalidRequirementException {
        return new PodInfoBuilder(
                getPodInstanceRequirement(taskPort, Collections.emptyList()),
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                taskInfos,
                TestConstants.FRAMEWORK_ID,
                true);
    }

    private PodInfoBuilder getPodInfoBuilderOnBridgeNetwork(int taskPort, Collection<Protos.TaskInfo> taskInfos)
            throws InvalidRequirementException {
        return new PodInfoBuilder(
                getPodInstanceRequirement(taskPort, Arrays.asList(DcosConstants.DEFAULT_BRIDGE_NETWORK)),
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                taskInfos,
                TestConstants.FRAMEWORK_ID,
                true);
    }
}
