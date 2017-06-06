package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class NamedVIPEvaluationStageTest {
    @Test
    public void testDiscoveryInfoPopulated() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(10000, Collections.emptyList());

        // Evaluate stage
        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(10000, Optional.empty());
        EvaluationOutcome outcome = vipEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(DiscoveryInfo.Visibility.CLUSTER, discoveryInfo.getVisibility());

        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), 10000);
        Assert.assertEquals(port.getProtocol(), "sctp");

        Protos.Label vipLabel = port.getLabels().getLabels(0);
        Assert.assertEquals("pod-type-0-test-task-name", discoveryInfo.getName());
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");
    }

    @Test
    public void testVIPIsReused() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource offeredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

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

        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(10000, taskInfos);
        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(10000, Optional.of(resourceId));

        EvaluationOutcome outcome = vipEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(1, discoveryInfo.getPorts().getPortsList().size());
        Assert.assertEquals(1, discoveryInfo.getPorts().getPorts(0).getLabels().getLabelsList().size());
    }

    @Test
    public void testPortNumberIsUpdated() throws InvalidRequirementException {
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedPorts(8000, 8000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

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

        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(8000, taskInfos);

        // Update the resource to have a different port, so that the TaskInfo's DiscoveryInfo mirrors the case where
        // a new port has been requested but we want to reuse the old VIP definition.

        NamedVIPEvaluationStage vipEvaluationStage = getEvaluationStage(8000, Optional.empty());
        EvaluationOutcome outcome = vipEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(1, discoveryInfo.getPorts().getPortsList().size());
        Assert.assertEquals(1, discoveryInfo.getPorts().getPorts(0).getLabels().getLabelsList().size());
        Assert.assertEquals(8000, discoveryInfo.getPorts().getPorts(0).getNumber());
    }

    private NamedVIPEvaluationStage getEvaluationStage(int taskPort, Optional<String> resourceId) {
        return new NamedVIPEvaluationStage(
                getNamedVIPSpec(taskPort),
                TestConstants.TASK_NAME,
                resourceId);
    }

    private NamedVIPSpec getNamedVIPSpec(int taskPort) {
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
                80);
    }

    private PodInstanceRequirement getPodInstanceRequirement(int taskPort) {
        // Build Pod
        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .id("resourceSet")
                .cpus(1.0)
                .addResource(getNamedVIPSpec(taskPort))
                .build();
        CommandSpec commandSpec = DefaultCommandSpec.newBuilder(TestConstants.POD_TYPE)
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

    private PodInfoBuilder getPodInfoBuilder(int taskPort, Collection<Protos.TaskInfo> taskInfos) throws InvalidRequirementException {
        return new PodInfoBuilder(
                getPodInstanceRequirement(taskPort),
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                taskInfos);
    }
}
