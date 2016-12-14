package com.mesosphere.sdk.offer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.executor.ExecutorUtils;
import com.mesosphere.sdk.offer.constrain.PlacementRule;
import com.mesosphere.sdk.offer.constrain.PlacementUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecification;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.PersistentOperationRecorder;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testing.CuratorTestUtils;
import com.mesosphere.sdk.testutils.*;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.scheduler.plan.PodInstanceRequirement;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.util.*;

import static org.junit.Assert.assertNotEquals;

public class OfferEvaluatorTest {

    private static final String ROOT_ZK_PATH = "/test-root-path";
    private static TestingServer testZk;
    private static EnvironmentVariables environmentVariables;
    private OfferRequirementProvider offerRequirementProvider;
    private StateStore stateStore;
    private OfferEvaluator evaluator;
    private PersistentOperationRecorder operationRecorder;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");
        environmentVariables.set("LIBMESOS_URI", "");
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
        offerRequirementProvider = new DefaultOfferRequirementProvider(
                new DefaultTaskConfigRouter(),
                stateStore,
                UUID.randomUUID());
        evaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
        operationRecorder = new PersistentOperationRecorder(stateStore);
    }

    @Test
    public void testReserveTaskDynamicPort() throws InvalidRequirementException {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Resource desiredDynamicPort = DynamicPortRequirement.getDesiredDynamicPort(
                TestConstants.PORT_NAME,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredDynamicPort);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(2, fulfilledPortResource.getReservation().getLabels().getLabelsCount());

        Label dynamicPortLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("dynamic_port", dynamicPortLabel.getKey());
        Assert.assertEquals(TestConstants.PORT_NAME, dynamicPortLabel.getValue());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(1);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        Assert.assertEquals(1, taskInfo.getCommand().getEnvironment().getVariablesCount());
        Environment.Variable variable = taskInfo.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(TestConstants.PORT_NAME, variable.getName());
        Assert.assertEquals(String.valueOf(10000), variable.getValue());
    }

    @Test
    public void testReserveExecutorDynamicPort() throws InvalidRequirementException {
        Resource offeredCpu = ResourceTestUtils.getUnreservedCpu(1.0);
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);

        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredDynamicPort = DynamicPortRequirement.getDesiredDynamicPort(
                TestConstants.PORT_NAME,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        OfferRequirement offerReq = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(TaskTestUtils.getTaskInfo(desiredCpu)),
                Optional.of(TaskTestUtils.getExecutorInfo(desiredDynamicPort)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerReq,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredCpu, offeredPorts))));

        Assert.assertEquals(3, recommendations.size());

        Operation launchOperation = recommendations.get(2).getOperation();
        ExecutorInfo executorInfo = launchOperation.getLaunch().getTaskInfos(0).getExecutor();
        Resource fulfilledPortResource = executorInfo.getResources(0);
        Assert.assertEquals(2, fulfilledPortResource.getReservation().getLabels().getLabelsCount());

        Label dynamicPortLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("dynamic_port", dynamicPortLabel.getKey());
        Assert.assertEquals(TestConstants.PORT_NAME, dynamicPortLabel.getValue());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(1);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        Assert.assertEquals(1, executorInfo.getCommand().getEnvironment().getVariablesCount());
        Environment.Variable variable = executorInfo.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(TestConstants.PORT_NAME, variable.getName());
        Assert.assertEquals(String.valueOf(10000), variable.getValue());

        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledCpuResource = taskInfo.getResources(0);
        Assert.assertEquals(1, fulfilledCpuResource.getReservation().getLabels().getLabelsCount());

        resourceIdLabel = fulfilledCpuResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        Assert.assertEquals(0, taskInfo.getCommand().getEnvironment().getVariablesCount());
    }

    @Test
    public void testReserveTaskAndExecutorDynamicPort() throws InvalidRequirementException, InvalidProtocolBufferException {
        Resource offeredCpu = ResourceTestUtils.getUnreservedCpu(1.0);
        Resource offeredExecutorPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Resource offeredTaskPorts = ResourceTestUtils.getUnreservedPorts(11000, 11000);

        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredExecutorDynamicPort = DynamicPortRequirement.getDesiredDynamicPort(
                TestConstants.PORT_NAME,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
        String taskPortName = "TASK_PORT";
        Resource desiredTaskDynamicPort = DynamicPortRequirement.getDesiredDynamicPort(
                taskPortName,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        OfferRequirement offerReq = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(TaskTestUtils.getTaskInfo(Arrays.asList(desiredCpu, desiredTaskDynamicPort))),
                Optional.of(TaskTestUtils.getExecutorInfo(desiredExecutorDynamicPort)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerReq,
                Arrays.asList(OfferTestUtils.getOffer(
                        Arrays.asList(offeredCpu, offeredExecutorPorts, offeredTaskPorts))));

        Assert.assertEquals(4, recommendations.size());

        Operation launchOperation = recommendations.get(3).getOperation();
        ExecutorInfo executorInfo = launchOperation.getLaunch().getTaskInfos(0).getExecutor();
        Resource fulfilledExecutorPortResource = executorInfo.getResources(0);
        Assert.assertEquals(2, fulfilledExecutorPortResource.getReservation().getLabels().getLabelsCount());

        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledTaskPortResource = taskInfo.getResources(1);
        Assert.assertEquals(2, fulfilledTaskPortResource.getReservation().getLabels().getLabelsCount());

        Label dynamicPortExecutorLabel = fulfilledExecutorPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("dynamic_port", dynamicPortExecutorLabel.getKey());
        Assert.assertEquals(TestConstants.PORT_NAME, dynamicPortExecutorLabel.getValue());

        Label dynamicPortTaskLabel = fulfilledTaskPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("dynamic_port", dynamicPortTaskLabel.getKey());
        Assert.assertEquals(taskPortName, dynamicPortTaskLabel.getValue());

        Label resourceIdLabel = fulfilledExecutorPortResource.getReservation().getLabels().getLabels(1);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        Label resourceIdTaskLabel = fulfilledTaskPortResource.getReservation().getLabels().getLabels(1);
        Assert.assertEquals("resource_id", resourceIdTaskLabel.getKey());

        Assert.assertEquals(1, executorInfo.getCommand().getEnvironment().getVariablesCount());
        Environment.Variable executorVariable = executorInfo.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(TestConstants.PORT_NAME, executorVariable.getName());
        Assert.assertEquals(String.valueOf(10000), executorVariable.getValue());

        fulfilledExecutorPortResource = taskInfo.getResources(0);
        Assert.assertEquals(1, fulfilledExecutorPortResource.getReservation().getLabels().getLabelsCount());

        resourceIdLabel = fulfilledExecutorPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        CommandInfo command = CommonTaskUtils.unpackTaskInfo(taskInfo).getCommand();
        Assert.assertEquals(0, taskInfo.getCommand().getEnvironment().getVariablesCount());
        Assert.assertEquals(1, command.getEnvironment().getVariablesCount());

        Environment.Variable taskVariable = command.getEnvironment().getVariables(0);
        Assert.assertEquals(taskPortName, taskVariable.getName());
        Assert.assertEquals(String.valueOf(11000), taskVariable.getValue());
    }

    @Test
    public void testReserveTaskNamedVIPPort() throws InvalidRequirementException {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Resource desiredNamedVIPPort = NamedVIPPortRequirement.getDesiredNamedVIPPort(
                TestConstants.VIP_KEY,
                TestConstants.VIP_NAME,
                (long) 10000,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredNamedVIPPort);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(3, fulfilledPortResource.getReservation().getLabels().getLabelsCount());

        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label namedVIPPortKeyLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("vip_key", namedVIPPortKeyLabel.getKey());
        Assert.assertEquals(TestConstants.VIP_KEY, namedVIPPortKeyLabel.getValue());

        Label namedVIPPortNameLabel = fulfilledPortResource.getReservation().getLabels().getLabels(1);
        Assert.assertEquals("vip_value", namedVIPPortNameLabel.getKey());
        Assert.assertEquals(TestConstants.VIP_NAME, namedVIPPortNameLabel.getValue());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(2);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertEquals(vipLabel.getKey(), "VIP_TEST");
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":10000");
    }

    @Test
    public void testReserveExecutorNamedVIPPort() throws InvalidRequirementException {
        Resource offeredCpu = ResourceTestUtils.getUnreservedCpu(1.0);
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);

        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredNamedVIPPort = NamedVIPPortRequirement.getDesiredNamedVIPPort(
                TestConstants.VIP_KEY,
                TestConstants.VIP_NAME,
                (long) 10000,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        OfferRequirement offerReq = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(TaskTestUtils.getTaskInfo(desiredCpu)),
                Optional.of(TaskTestUtils.getExecutorInfo(desiredNamedVIPPort)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerReq,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredCpu, offeredPorts))));

        Assert.assertEquals(3, recommendations.size());

        Operation launchOperation = recommendations.get(2).getOperation();
        ExecutorInfo executorInfo = launchOperation.getLaunch().getTaskInfos(0).getExecutor();
        Resource fulfilledPortResource = executorInfo.getResources(0);
        Assert.assertEquals(3, fulfilledPortResource.getReservation().getLabels().getLabelsCount());

        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label namedVIPPortKeyLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("vip_key", namedVIPPortKeyLabel.getKey());
        Assert.assertEquals(TestConstants.VIP_KEY, namedVIPPortKeyLabel.getValue());

        Label namedVIPPortNameLabel = fulfilledPortResource.getReservation().getLabels().getLabels(1);
        Assert.assertEquals("vip_value", namedVIPPortNameLabel.getKey());
        Assert.assertEquals(TestConstants.VIP_NAME, namedVIPPortNameLabel.getValue());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(2);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledCpuResource = taskInfo.getResources(0);
        Assert.assertEquals(1, fulfilledCpuResource.getReservation().getLabels().getLabelsCount());

        resourceIdLabel = fulfilledCpuResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = executorInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), executorInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertEquals(vipLabel.getKey(), "VIP_TEST");
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":10000");
    }

    @Test
    public void testReserveTaskExecutorInsufficient() throws InvalidRequirementException {
        Resource desiredTaskCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredExecutorCpu = desiredTaskCpu;
        Resource insufficientOfferedResource = ResourceUtils.getUnreservedScalar("cpus", 1.0);

        OfferRequirement offerReq = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(TaskTestUtils.getTaskInfo(desiredTaskCpu)),
                Optional.of(TaskTestUtils.getExecutorInfo(desiredExecutorCpu)));
        List<Offer> offers = Arrays.asList(OfferTestUtils.getOffer(insufficientOfferedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerReq, offers);
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveCreateLaunchMountVolume() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(3, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource =
            reserveOperation
            .getReserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(2000, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, reserveResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());

        // Validate CREATE Operation
        String resourceId = getFirstLabel(reserveResource).getValue();
        Operation createOperation = recommendations.get(1).getOperation();
        Resource createResource =
            createOperation
            .getCreate()
            .getVolumesList()
            .get(0);

        Assert.assertEquals(resourceId, getFirstLabel(createResource).getValue());
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, createResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());

        // Validate LAUNCH Operation
        String persistenceId = createResource.getDisk().getPersistence().getId();
        Operation launchOperation = recommendations.get(2).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testUpdateMountVolumeSuccess() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(1500, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(updatedResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(1, recommendations.size());

        Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource = launchOperation
                .getLaunch()
                .getTaskInfosList()
                .get(0)
                .getResourcesList()
                .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getFirstLabel(updatedResource).getValue(), getFirstLabel(launchResource).getValue());
        Assert.assertEquals(updatedResource.getDisk().getPersistence().getId(), launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testUpdateMountVolumeFailure() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(2500, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(updatedResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testFailToCreateVolumeWithWrongResource() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000);
        Resource wrongOfferedResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(wrongOfferedResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveCreateLaunchRootVolume() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1500);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(3, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource =
            reserveOperation
            .getReserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1500, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());

        // Validate CREATE Operation
        String resourceId = getFirstLabel(reserveResource).getValue();
        Operation createOperation = recommendations.get(1).getOperation();
        Resource createResource =
            createOperation
            .getCreate()
            .getVolumesList()
            .get(0);

        Assert.assertEquals(resourceId, getFirstLabel(createResource).getValue());
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());

        // Validate LAUNCH Operation
        String persistenceId = createResource.getDisk().getPersistence().getId();
        Operation launchOperation = recommendations.get(2).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
    }

    @Test
    public void testFailCreateRootVolume() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000 * 2);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(1000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testExpectedMountVolume() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedResource = ResourceTestUtils.getExpectedMountVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(expectedResource),
                        Arrays.asList(OfferTestUtils.getOffer(expectedResource)));
        Assert.assertEquals(1, recommendations.size());

        Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(1000, launchResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, launchResource.getRole());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PERSISTENCE_ID, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testExpectedRootVolume() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedResource = ResourceTestUtils.getExpectedRootVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(expectedResource),
                        Arrays.asList(OfferTestUtils.getOffer(expectedResource)));
        Assert.assertEquals(1, recommendations.size());

        Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(1000, launchResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, launchResource.getRole());
        Assert.assertEquals(TestConstants.PERSISTENCE_ID, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReserveLaunchScalar() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource =
            reserveOperation
            .getReserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getFirstLabel(reserveResource).getValue(), getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testCustomExecutorReserveLaunchScalar() throws InvalidRequirementException {
        Resource desiredTaskResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredExecutorResource = ResourceTestUtils.getDesiredMem(2.0);

        Resource offeredTaskResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);
        Resource offeredExecutorResource = ResourceUtils.getUnreservedScalar("mem", 2.0);

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(desiredTaskResource);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(desiredExecutorResource);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(taskInfo), Optional.of(execInfo)),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredTaskResource, offeredExecutorResource))));
        Assert.assertEquals(3, recommendations.size());

        // Validate Executor RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource =
            reserveOperation
            .getReserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals("mem", reserveResource.getName());
        Assert.assertEquals(2.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        String executorResourceId = getFirstLabel(reserveResource).getValue();
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, executorResourceId.length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate Task RESERVE Operation
        reserveOperation = recommendations.get(1).getOperation();
        reserveResource =
            reserveOperation
            .getReserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals("cpus", reserveResource.getName());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(2).getOperation();
        TaskInfo outTaskInfo =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0);

        Assert.assertTrue(outTaskInfo.hasExecutor());
        ExecutorInfo outExecInfo = outTaskInfo.getExecutor();
        Assert.assertEquals(executorResourceId, getFirstLabel(outExecInfo.getResourcesList().get(0)).getValue());

        Resource launchResource = outTaskInfo.getResourcesList().get(0);
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getFirstLabel(reserveResource).getValue(), getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReuseCustomExecutorReserveLaunchScalar() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredTaskResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredTaskResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        Resource desiredExecutorResource = ResourceTestUtils.getExpectedScalar("mem", 2.0, resourceId);
        Resource offeredExecutorResource = desiredExecutorResource;

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(desiredTaskResource);
        ExecutorInfo execInfo = TaskTestUtils.getExistingExecutorInfo(desiredExecutorResource);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(taskInfo), Optional.of(execInfo)),
                Arrays.asList(OfferTestUtils.getOffer(
                        TestConstants.EXECUTOR_ID,
                        Arrays.asList(offeredTaskResource, offeredExecutorResource))));
        Assert.assertEquals(2, recommendations.size());

        // Validate Task RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation
                .getReserve()
                .getResourcesList()
                .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals("cpus", reserveResource.getName());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo outTaskInfo = launchOperation.getLaunch().getTaskInfosList().get(0);

        Assert.assertTrue(outTaskInfo.hasExecutor());
        Resource launchResource = outTaskInfo.getResourcesList().get(0);
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getFirstLabel(reserveResource).getValue(), getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testLaunchExpectedScalar() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(desiredResource)));
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
    public void testLaunchAttributesEmbedded() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Offer.Builder offerBuilder = OfferTestUtils.getOffer(desiredResource).toBuilder();
        Attribute.Builder attrBuilder =
                offerBuilder.addAttributesBuilder().setName("rack").setType(Value.Type.TEXT);
        attrBuilder.getTextBuilder().setValue("foo");
        attrBuilder = offerBuilder.addAttributesBuilder().setName("diskspeed").setType(Value.Type.SCALAR);
        attrBuilder.getScalarBuilder().setValue(1234.5678);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
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

    @Test
    public void testReserveLaunchExpectedScalar() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource, unreservedResource))));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource =
            reserveOperation
            .getReserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(reserveResource).getValue());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
        Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testFailReserveLaunchExpectedScalar() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testUnreserveLaunchExpectedScalar() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(2, recommendations.size());

        // Validate UNRESERVE Operation
        Operation unreserveOperation = recommendations.get(0).getOperation();
        Resource unreserveResource =
            unreserveOperation
            .getUnreserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOperation.getType());
        Assert.assertEquals(1.0, unreserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, unreserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, unreserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(unreserveResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(unreserveResource).getValue());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
        Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testAvoidAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getOfferRequirement(
                        desiredCpu,
                        Arrays.asList(TestConstants.AGENT_ID.getValue()),
                        Collections.emptyList()),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                getOfferRequirement(
                        desiredCpu,
                        Arrays.asList("some-random-agent"),
                        Collections.emptyList()),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testColocateAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getOfferRequirement(
                        desiredCpu,
                        Collections.emptyList(),
                        Arrays.asList("some-random-agent")),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                getOfferRequirement(
                        desiredCpu,
                        Collections.emptyList(),
                        Arrays.asList(TestConstants.AGENT_ID.getValue())),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testRejectOfferWithoutExpectedExecutorId() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, resourceId);

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        Optional<ExecutorInfo> execInfo = Optional.of(TaskTestUtils.getExecutorInfo(expectedExecutorMem));

        // Set incorrect ExecutorID
        execInfo = Optional.of(ExecutorInfo.newBuilder(execInfo.get())
                .setExecutorId(ExecutorUtils.toExecutorId(execInfo.get().getName()))
                .build());

        OfferRequirement offerRequirement =
                OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(taskInfo), execInfo);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerRequirement,
                Arrays.asList(OfferTestUtils.getOffer(
                        Arrays.asList(expectedTaskCpu, expectedExecutorMem))));

        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testAcceptOfferWithExpectedExecutorId() throws Exception {
        String taskResourceId = UUID.randomUUID().toString();
        String executorResourceId = UUID.randomUUID().toString();
        Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, taskResourceId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, executorResourceId);

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        ExecutorInfo execInfo = TaskTestUtils.getExistingExecutorInfo(expectedExecutorMem);

        OfferRequirement offerRequirement =
                OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(taskInfo), Optional.of(execInfo));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerRequirement,
                Arrays.asList(OfferTestUtils.getOffer(
                        TestConstants.EXECUTOR_ID,
                        Arrays.asList(expectedTaskCpu, expectedExecutorMem))));

        Assert.assertEquals(1, recommendations.size());
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    }

    @Test
    public void testRelaunchTaskWithCustomExecutor() throws Exception {
        String taskResourceId = UUID.randomUUID().toString();
        String executorResourceId = UUID.randomUUID().toString();
        Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, taskResourceId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, executorResourceId);

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(expectedExecutorMem);

        OfferRequirement offerRequirement =
                OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(taskInfo), Optional.of(execInfo));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerRequirement,
                Arrays.asList(OfferTestUtils.getOffer(null, Arrays.asList(expectedTaskCpu, expectedExecutorMem))));

        Assert.assertEquals(1, recommendations.size());
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        TaskInfo launchedTaskInfo = launchOperation.getLaunch().getTaskInfosList().get(0);
        assertNotEquals("", launchedTaskInfo.getExecutor().getExecutorId().getValue());
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Resource desiredTask0Cpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredTask1Cpu = ResourceTestUtils.getDesiredCpu(2.0);
        Resource desiredExecutorCpu = ResourceTestUtils.getDesiredCpu(3.0);
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 6.0);

        TaskInfo taskInfo0 = TaskTestUtils.getTaskInfo(desiredTask0Cpu);
        TaskInfo taskInfo1 = TaskTestUtils.getTaskInfo(desiredTask1Cpu);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(desiredExecutorCpu);
        OfferRequirement offerRequirement = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(taskInfo0, taskInfo1),
                Optional.of(execInfo));
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                offerRequirement,
                Arrays.asList(OfferTestUtils.getOffer(null, Arrays.asList(offeredResource))));

        Assert.assertEquals(5, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Operation launchOp0 = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp0.getType());
        Operation launchOp1 = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp1.getType());
        Protos.ExecutorID launch0ExecutorId = launchOp0.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
        Protos.ExecutorID launch1ExecutorId = launchOp1.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
        Assert.assertEquals(launch0ExecutorId, launch1ExecutorId);
    }

    @Test
    public void testLaunchNotOnFirstOffer() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource insufficientOffer = ResourceUtils.getUnreservedScalar("mem", 2.0);
        Resource sufficientOffer = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
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
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                new PodInstanceRequirement(podInstance, Arrays.asList("format"));

        Resource sufficientResource = ResourceUtils.getUnreservedScalar("cpus", 3.0);
        Offer sufficientOffer = OfferTestUtils.getOffer(sufficientResource);

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(4, recommendations.size());

        // Validate RESERVE Operations
        Operation reserveOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        reserveOperation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());

        // Validate LAUNCH Operations
        Operation launchOperation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        launchOperation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        recordOperations(recommendations, sufficientOffer);

        // Launch Task with RUNNING goal state, later.
        podInstanceRequirement = new PodInstanceRequirement(podInstance, Arrays.asList("node"));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // Providing sufficient, but unreserved resources should result in no operations.
        Assert.assertEquals(0, recommendations.size());

        String resourceId = offerRequirementProvider.getExistingOfferRequirement(podInstance, Arrays.asList("node"))
                .getTaskRequirements().stream()
                .flatMap(taskRequirement -> taskRequirement.getResourceRequirements().stream())
                .map(resourceRequirement -> resourceRequirement.getResourceId())
                .findFirst()
                .get();

        Resource expectedResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Offer expectedOffer = OfferTestUtils.getOffer(expectedResource);
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(expectedOffer));
        // Providing the expected reserved resources should result in a LAUNCH operation.
        Assert.assertEquals(1, recommendations.size());
        launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    }

    private static OfferRequirement getOfferRequirement(
            Protos.Resource resource, List<String> avoidAgents, List<String> collocateAgents)
                    throws InvalidRequirementException {
        Optional<PlacementRule> placement = PlacementUtils.getAgentPlacementRule(avoidAgents, collocateAgents);
        return OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(TaskTestUtils.getTaskInfo(resource)),
                Optional.empty(),
                placement);
    }

    private static Label getFirstLabel(Resource resource) {
        return resource.getReservation().getLabels().getLabels(0);
    }

    private void recordOperations(List<OfferRecommendation> recommendations, Offer offer) throws Exception {
        for (OfferRecommendation recommendation : recommendations) {
            operationRecorder.record(recommendation.getOperation(), offer);
        }
    }
}
