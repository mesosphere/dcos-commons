package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
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

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnNetwork(10000, Collections.emptyList(), Optional.empty());

        // Evaluate stage
        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnNetwork(10000, Optional.empty(), Optional.empty());
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
        String overlayNetwork = "dcos";

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnNetwork(
                containerPort, Collections.emptyList(), Optional.of(overlayNetwork));

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnNetwork(
                containerPort, Optional.empty(), Optional.of(overlayNetwork));

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

        Collection<EndpointUtils.VipInfo> vips = AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, port);
        Assert.assertEquals(1, vips.size());
        EndpointUtils.VipInfo vip = vips.iterator().next();
        Assert.assertEquals("test-vip", vip.getVipName());
        Assert.assertEquals(80, vip.getVipPort());

        assertIsOverlayLabel(port.getLabels().getLabels(1));
    }

    @Test
    public void testDiscoveryInfoOnBridgeNetwork() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        Integer containerPort = 10000;  // non-offered port
        String bridgeNetwork = "mesos-bridge";

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnNetwork(
                containerPort, Collections.emptyList(), Optional.of(bridgeNetwork));

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnNetwork(
                containerPort, Optional.empty(), Optional.of(bridgeNetwork));

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

        assertIsBridgeLabel(port.getLabels().getLabels(1));
    }

    @Test
    public void testDiscoveryInfoWhenOnOverlayWithDynamicPort() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        Integer containerPort = 0;  // non-offered port
        String overlayNetwork = "dcos";

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderOnNetwork(
                containerPort, Collections.emptyList(), Optional.of(overlayNetwork));

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStageOnNetwork(
                containerPort, Optional.empty(), Optional.of(overlayNetwork));

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

        Collection<EndpointUtils.VipInfo> vips = AuxLabelAccess.getVIPsFromLabels(TestConstants.TASK_NAME, port);
        Assert.assertEquals(1, vips.size());
        EndpointUtils.VipInfo vip = vips.iterator().next();
        Assert.assertEquals("test-vip", vip.getVipName());
        Assert.assertEquals(80, vip.getVipPort());

        assertIsOverlayLabel(port.getLabels().getLabels(1));
    }

    private static NamedVIPEvaluationStage getEvaluationStageOnNetwork(
            int taskPort, Optional<String> resourceId, Optional<String> network) {
        Collection<String> networks = network.isPresent()
                ? Collections.singleton(network.get()) : Collections.emptyList();
        return new NamedVIPEvaluationStage(getNamedVIPSpec(taskPort, networks), TestConstants.TASK_NAME, resourceId);
    }

    private static NamedVIPSpec getNamedVIPSpec(int taskPort, Collection<String> networkNames) {
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

    private static PodInstanceRequirement getPodInstanceRequirement(int taskPort, Collection<String> networkNames) {
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

    private static PodInfoBuilder getPodInfoBuilderOnNetwork(
            int taskPort, Collection<Protos.TaskInfo> taskInfos, Optional<String> network)
            throws InvalidRequirementException {
        Collection<String> networks = network.isPresent()
                ? Collections.singleton(network.get()) : Collections.emptyList();
        return new PodInfoBuilder(
                getPodInstanceRequirement(taskPort, networks),
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                taskInfos,
                TestConstants.FRAMEWORK_ID,
                true,
                Collections.emptyMap());
    }

    private static void assertIsOverlayLabel(Protos.Label label) {
        Assert.assertEquals("network-scope", label.getKey());
        Assert.assertEquals("container", label.getValue());
    }

    private static void assertIsBridgeLabel(Protos.Label label) {
        Assert.assertEquals("network-scope", label.getKey());
        Assert.assertEquals("host", label.getValue());
    }
}
