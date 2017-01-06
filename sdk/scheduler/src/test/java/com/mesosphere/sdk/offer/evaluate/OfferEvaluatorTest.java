package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.constrain.PlacementRule;
import com.mesosphere.sdk.offer.constrain.PlacementUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TaskSpec;
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
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("PMD")
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
        offerRequirementProvider = new DefaultOfferRequirementProvider(stateStore, UUID.randomUUID());
        evaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
        operationRecorder = new PersistentOperationRecorder(stateStore);
    }

    @Test
    public void testReserveTaskDynamicPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);

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
        Assert.assertEquals("PORT_" + TestConstants.PORT_NAME, variable.getName());
        Assert.assertEquals(String.valueOf(10000), variable.getValue());
    }

    @Test
    public void testLaunchExpectedPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPortPodInstanceRequirement(desiredResource, "single-port.yml"),
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
    public void testLaunchExpectedDynamicPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPortPodInstanceRequirement(desiredResource, "dynamic-port.yml"),
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
    public void testLaunchExpectedMultiplePorts() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10001, resourceId);

        PodInstanceRequirement podInstanceRequirement = getExistingPortPodInstanceRequirement(
                desiredResource, "multiple-port-with-finished.yml");

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
    public void testReserveTaskMultipleDynamicPorts() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10001);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getMultipleDynamicPortPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource fulfilledPortResource = reserveOperation.getReserve().getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, fulfilledPortResource.getRanges().getRange(0).getEnd());

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
        Assert.assertEquals("PORT_" + TestConstants.PORT_NAME, variable.getName());
        Assert.assertEquals(String.valueOf(10000), variable.getValue());

        variable = command.getEnvironment().getVariables(4);
        Assert.assertEquals("PORT_" + TestConstants.PORT_NAME + "2", variable.getName());
        Assert.assertEquals(String.valueOf(10001), variable.getValue());

        Assert.assertEquals(10000, taskPortResource.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, taskPortResource.getRanges().getRange(0).getEnd());
    }

    @Test
    public void testReserveTaskNamedVIPPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getNamedVIPPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":8080");
    }

    @Test
    public void testReserveTaskDynamicVIPPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getDynamicVIPPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":8080");
    }

    @Test
    public void testReserveCreateLaunchMountVolume() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(desiredResource, true);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        podInstanceRequirement,
                        Arrays.asList(getOffer(offeredResource)));
        Assert.assertEquals(4, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(1).getOperation();
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
        Operation createOperation = recommendations.get(2).getOperation();
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
        Operation launchOperation = recommendations.get(3).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(1);

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
                        getExistingPodInstanceRequirement(updatedResource, true),
                        Arrays.asList(getOffer(offeredResource)));
        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation
                .getLaunch()
                .getTaskInfosList()
                .get(0)
                .getResourcesList()
                .get(1);

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
                getExistingPodInstanceRequirement(updatedResource, true),
                Arrays.asList(getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testFailToCreateVolumeWithWrongResource() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000);
        Resource wrongOfferedResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(desiredResource, true),
                Arrays.asList(getOffer(wrongOfferedResource)));
        Assert.assertEquals(0, recommendations.size());
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveCreateLaunchRootVolume() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1500);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(desiredResource, true),
                Arrays.asList(getOffer(offeredResource)));
        Assert.assertEquals(4, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(1).getOperation();
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
        Operation createOperation = recommendations.get(2).getOperation();
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
        Operation launchOperation = recommendations.get(3).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(1);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
    }

    @Test
    public void testFailCreateRootVolume() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000 * 2);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(1000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        getPodInstanceRequirement(desiredResource, true),
                        Arrays.asList(getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testExpectedMountVolume() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedResource = ResourceTestUtils.getExpectedMountVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        getExistingPodInstanceRequirement(expectedResource, true),
                        Arrays.asList(getOffer(expectedResource)));
        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(1);

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
    public void testExpectedRootVolume() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedResource = ResourceTestUtils.getExpectedRootVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        getExistingPodInstanceRequirement(expectedResource, true),
                        Arrays.asList(getOffer(expectedResource)));
        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource =
            launchOperation
            .getLaunch()
            .getTaskInfosList()
            .get(0)
            .getResourcesList()
            .get(1);

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
    public void testReserveLaunchScalar() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(desiredResource),
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
    public void testLaunchExpectedScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(desiredResource, false),
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
    public void testLaunchAttributesEmbedded() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Offer.Builder offerBuilder = OfferTestUtils.getOffer(desiredResource).toBuilder();
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

    @Test
    public void testReserveLaunchExpectedScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(desiredResource, false),
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
    public void testFailReserveLaunchExpectedScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(desiredResource, false),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testUnreserveLaunchExpectedScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(desiredResource, false),
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
                getPodInstanceRequirement(
                        desiredCpu, Arrays.asList(TestConstants.AGENT_ID.getValue()), Collections.emptyList(), false),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                getPodInstanceRequirement(
                        desiredCpu, Arrays.asList("some-random-agent"), Collections.emptyList(), false),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testColocateAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(
                        desiredCpu, Collections.emptyList(), Arrays.asList("some-random-agent"), false),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                getPodInstanceRequirement(
                        desiredCpu, Collections.emptyList(), Arrays.asList(TestConstants.AGENT_ID.getValue()), false),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 3.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getMultipleTaskPodInstanceRequirement(),
                Arrays.asList(OfferTestUtils.getOffer(null, Arrays.asList(offeredResource))));

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
                PodInstanceRequirement.create(podInstance, Arrays.asList("format"));

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 3.0),
                ResourceUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate RESERVE Operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());

        // Validate CREATE Operation
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());

        // Validate LAUNCH Operations
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        operation = recommendations.get(5).getOperation();
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
        Assert.assertEquals(resourceIds.toString(), 2, resourceIds.size());

        Offer expectedOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceIds.get(0)),
                ResourceTestUtils.getExpectedScalar("disk", 50.0, resourceIds.get(1))));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(expectedOffer));
        // Providing the expected reserved resources should result in a LAUNCH operation.
        Assert.assertEquals(1, recommendations.size());
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
    }

    @Test
    public void testRelaunchFailedPod() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.create(podInstance, Arrays.asList("format"));

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 3.0),
                ResourceUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate RESERVE Operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());

        // Validate CREATE Operation
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());

        // Validate LAUNCH Operations
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        operation = recommendations.get(5).getOperation();
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
        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate RESERVE Operations
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());

        // Validate CREATE Operation
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());

        // Validate LAUNCH Operations
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
    }

    private PodInstanceRequirement getPodInstanceRequirement(Resource resource) throws Exception {
        return getPodInstanceRequirement(resource, false);
    }

    private PodInstanceRequirement getDynamicPortPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, "dynamic-port.yml");
    }

    private PodInstanceRequirement getExistingPortPodInstanceRequirement(
            Resource resource, String yamlFile) throws Exception {
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, yamlFile);
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
                .setName("PORT_" + TestConstants.PORT_NAME)
                .setValue(Long.toString(resource.getRanges().getRange(0).getBegin()));
        offerRequirement.updateTaskRequirement(TestConstants.TASK_NAME, existingTaskInfo.build());
        stateStore.storeTasks(Arrays.asList(existingTaskInfo.build()));

        return podInstanceRequirement;
    }

    private PodInstanceRequirement getMultipleDynamicPortPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                "multiple-dynamic-port.yml");
    }

    private PodInstanceRequirement getNamedVIPPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, "named-vip.yml");
    }

    private PodInstanceRequirement getDynamicVIPPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                "dynamic-vip-port.yml");
    }

    private PodInstanceRequirement getPodInstanceRequirement(
            Resource resource,
            List<String> avoidAgents,
            List<String> collocateAgents,
            boolean isVolume) throws Exception {
        return getPodInstanceRequirement(
                Arrays.asList(resource), avoidAgents, collocateAgents, isVolume, "single-task.yml");
    }

    private PodInstanceRequirement getPodInstanceRequirement(
            Collection<Resource> resources,
            List<String> avoidAgents,
            List<String> collocateAgents,
            boolean isVolume,
            String yamlFile) throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(yamlFile).getFile());
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        if (!resources.isEmpty()) {
            podSpec = isVolume ?
                    OfferRequirementTestUtils.withVolume(
                            serviceSpec.getPods().get(0), resources.iterator().next(), serviceSpec.getPrincipal()) :
                    OfferRequirementTestUtils.withResources(
                            serviceSpec.getPods().get(0),
                            resources,
                            serviceSpec.getPrincipal(),
                            avoidAgents,
                            collocateAgents);
        }

        return PodInstanceRequirement.create(
                new DefaultPodInstance(podSpec, 0),
                podSpec.getTasks().stream().map(t -> t.getName()).collect(Collectors.toList()));
    }

    private PodInstanceRequirement getExistingPodInstanceRequirement(
            Resource resource, boolean isVolume) throws Exception {
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(resource);
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(resource, isVolume);
        TaskInfo existingTaskInfo = offerRequirement.getTaskRequirements().iterator().next()
                .getTaskInfo()
                .toBuilder()
                .setName(TaskSpec.getInstanceName(
                        podInstanceRequirement.getPodInstance(),
                        podInstanceRequirement.getPodInstance().getPod().getTasks().get(0)))
                .build();
        stateStore.storeTasks(Arrays.asList(existingTaskInfo));

        return podInstanceRequirement;
    }

    private PodInstanceRequirement getPodInstanceRequirement(Resource resource, boolean isVolume) throws Exception {
        return getPodInstanceRequirement(resource, Collections.emptyList(), Collections.emptyList(), isVolume);
    }

    private PodInstanceRequirement getMultipleTaskPodInstanceRequirement() throws Exception {
        return getPodInstanceRequirement(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, "multiple-task.yml");
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

    private static Offer getOffer(Resource resource) {
        return OfferTestUtils.getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 1.0),
                ResourceUtils.getUnreservedScalar("mem", 512),
                resource));
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
