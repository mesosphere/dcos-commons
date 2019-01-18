package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.evaluate.placement.TestPlacementUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class PortEvaluationStageTest extends DefaultCapabilitiesTestSuite {
    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    private Protos.Value getPort(int port) {
        return Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES)
                .setRanges(Protos.Value.Ranges.newBuilder()
                        .addRange(Protos.Value.Range.newBuilder()
                                .setBegin(port)
                                .setEnd(port)))
                .build();
    }

    private PodInfoBuilder getPodInfoBuilder(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException {
        return new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                PodTestUtils.getTemplateUrlFactory(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Collections.emptyList(),
                TestConstants.FRAMEWORK_ID,
                Collections.emptyMap());
    }

    private PodInstanceRequirement getPodInstanceRequirement(PortSpec... portSpecs) {
        // Build Pod

        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .id("resourceSet")
                .cpus(1.0)
                .addResources(Arrays.asList(portSpecs))
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
        PodSpec podSpec =
                DefaultPodSpec.newBuilder(TestConstants.POD_TYPE, 1, Arrays.asList(taskSpec)).build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        return PodInstanceRequirement.newBuilder(podInstance, Arrays.asList(TestConstants.TASK_NAME)).build();

    }

    private PodInstanceRequirement getPodInstanceWithPrereservedRole(PortSpec... portSpecs) {
        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
            .id("resourceSet")
            .cpus(1.0)
            .addResources(Arrays.asList(portSpecs))
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

        PodSpec podSpec = DefaultPodSpec.newBuilder(TestConstants.POD_TYPE, 5, Arrays.asList(taskSpec))
            .user("root")
            .preReservedRole("slave_public")
            .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        return PodInstanceRequirement.newBuilder(podInstance, Arrays.asList(TestConstants.TASK_NAME)).build();
    }

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(serviceSpecFileName).getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule(TestPlacementUtils.PASS)
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        return new DefaultPodInstance(serviceSpec.getPods().get(0), 0);
    }

    private Collection<String> getOverlayNetworkNames() {
        return Arrays.asList("dcos");
    }

    private PortSpec getPortSpec(PodInstance podInstance) {
        return (PortSpec) podInstance.getPod().getTasks().get(0).getResourceSet().getResources().stream()
                .filter(resourceSpec -> resourceSpec instanceof PortSpec)
                .findFirst()
                .get();
    }

    private static void checkDiscoveryInfo(Protos.DiscoveryInfo discoveryInfo,
                                       String expectedPortName,
                                       long expectedPort) {
        Assert.assertEquals(Constants.DEFAULT_TASK_DISCOVERY_VISIBILITY, discoveryInfo.getVisibility());
        List<Protos.Port> ports = discoveryInfo.getPorts().getPortsList().stream()
                .filter(p -> p.getName().equals(expectedPortName))
                .collect(Collectors.toList());
        Assert.assertTrue(String.format("Didn't find port with name %s, got ports %s", expectedPortName,
                ports.toString()), ports.size() == 1);
        Protos.Port port = ports.get(0);
        Assert.assertTrue(String.format("Port %s has incorrect number got %d should be %d",
                port.toString(), port.getNumber(), expectedPort), port.getNumber() == expectedPort);
        Assert.assertEquals(Constants.DISPLAYED_PORT_VISIBILITY, port.getVisibility());
    }

    @Test
    public void testPortResourceIsIgnoredOnOverlay() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        Integer requestedPort = 80;  // request a port that's not available in the offer.
        String expectedPortEnvVar = "PORT_TEST_IGNORED";
        String expectedPortName = "overlay-port-name";

        PortSpec.Builder builder = PortSpec.newBuilder()
                .envKey(expectedPortEnvVar)
                .portName(expectedPortName)
                .visibility(TestConstants.PORT_VISIBILITY)
                .networkNames(getOverlayNetworkNames());
        builder
                .value(getPort(requestedPort))
                .role(TestConstants.ROLE)
                .preReservedRole(Constants.ANY_ROLE)
                .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec, Collections.singleton(TestConstants.TASK_NAME), Optional.empty(), Optional.empty());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(), expectedPortName, 80);
        Assert.assertTrue("TaskInfo builder missing DiscoveryInfo", taskBuilder.hasDiscovery());

        Assert.assertEquals(0, taskBuilder.getResourcesCount());
        List<Protos.Environment.Variable> portEnvVars = taskBuilder.getCommand().getEnvironment().getVariablesList()
                .stream()
                .filter(variable -> variable.getName().equals(expectedPortEnvVar))
                .collect(Collectors.toList());
        Assert.assertEquals(1, portEnvVars.size());
        Protos.Environment.Variable variable = portEnvVars.get(0);
        Assert.assertEquals(variable.getName(), expectedPortEnvVar);
        Assert.assertEquals(variable.getValue(), requestedPort.toString());
    }

    @Test
    public void testDynamicPortResourceOnOverlay() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        Integer expectedDynamicOverlayPort = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START;
        String expectedDynamicOverlayPortEnvvar = "PORT_TEST_DYNAMIC_OVERLAY";
        String expectedPortName = "dyn-port-name";
        long expectedDynamicallyAssignedPort = 1025;

        PortSpec.Builder builder = PortSpec.newBuilder()
                .envKey(expectedDynamicOverlayPortEnvvar)
                .portName(expectedPortName)
                .visibility(TestConstants.PORT_VISIBILITY)
                .networkNames(getOverlayNetworkNames());
        builder
                .value(getPort(0))
                .role(TestConstants.ROLE)
                .preReservedRole(Constants.ANY_ROLE)
                .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec, Collections.singleton(TestConstants.TASK_NAME), Optional.empty(), Optional.empty());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(), expectedPortName, expectedDynamicallyAssignedPort);
        Assert.assertEquals(0, taskBuilder.getResourcesCount());
        List<Protos.Environment.Variable> portEnvVars = taskBuilder.getCommand().getEnvironment().getVariablesList()
                .stream()
                .filter(variable -> variable.getName().equals(expectedDynamicOverlayPortEnvvar))
                .collect(Collectors.toList());
        Assert.assertEquals(1, portEnvVars.size());
        Protos.Environment.Variable variable = portEnvVars.get(0);
        Assert.assertEquals(variable.getName(), expectedDynamicOverlayPortEnvvar);
        Assert.assertEquals(variable.getValue(), expectedDynamicOverlayPort.toString());
        Assert.assertEquals(variable.getName(), expectedDynamicOverlayPortEnvvar);
        Assert.assertEquals(variable.getValue(), expectedDynamicOverlayPort.toString());
    }

    @Test
    public void testDynamicPortResourceOnOverlayAndRequestedPort() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        Integer expectedExplicitOverlayPort = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START;
        Integer expectedDynamicOverlayPort = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START + 1;
        String expectedExplicitOverlayPortEnvvar = "PORT_TEST_EXPLICIT";
        String expextedDynamicOverlayPortEnvvar = "PORT_TEST_DYNAMIC";
        String expectedExplicitPortName = "explicit-port";
        String expectedDynamicPortName = "dynamic-port";

        PortSpec.Builder builder = PortSpec.newBuilder()
                .envKey(expectedExplicitOverlayPortEnvvar)
                .portName(expectedExplicitPortName)
                .visibility(TestConstants.PORT_VISIBILITY)
                .networkNames(getOverlayNetworkNames());
        builder
                .value(getPort(DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START))
                .role(TestConstants.ROLE)
                .preReservedRole(Constants.ANY_ROLE)
                .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        builder
                .envKey(expextedDynamicOverlayPortEnvvar)
                .portName(expectedDynamicPortName)
                .value(getPort(0));
        PortSpec dynamPortSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec, dynamPortSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        Assert.assertTrue(String.format("podInfoBuilder has incorrect number of pre-assigned overlay ports " +
                        "should be 1, got %s", podInfoBuilder.getAssignedOverlayPorts().size()),
                podInfoBuilder.getAssignedOverlayPorts().size() == 1);

        PortEvaluationStage portEvaluationStage_ = new PortEvaluationStage(
                portSpec, Collections.singleton(TestConstants.TASK_NAME), Optional.empty(), Optional.empty());
        EvaluationOutcome outcome0 = portEvaluationStage_.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome0.isPassing());
        Assert.assertEquals(0, outcome0.getOfferRecommendations().size());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                dynamPortSpec, Collections.singleton(TestConstants.TASK_NAME), Optional.empty(), Optional.empty());
        EvaluationOutcome outcome1 = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome1.isPassing());
        Assert.assertEquals(0, outcome1.getOfferRecommendations().size());

        Assert.assertTrue(String.format("podInfoBuilder has incorrect number of assigned overlay ports, " +
                        "should be 2 got %s", podInfoBuilder.getAssignedOverlayPorts().size()),
                podInfoBuilder.getAssignedOverlayPorts().size() == 2);
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(), expectedExplicitPortName, expectedExplicitOverlayPort);
        checkDiscoveryInfo(taskBuilder.getDiscovery(), expectedDynamicPortName, expectedDynamicOverlayPort);
        Assert.assertEquals(0, taskBuilder.getResourcesCount());
        Map<String, String> portEnvVarMap = taskBuilder.getCommand().getEnvironment().getVariablesList()
                .stream()
                .filter(variable -> variable.getName().equals(expectedExplicitOverlayPortEnvvar) ||
                        variable.getName().equals(expextedDynamicOverlayPortEnvvar))
                .collect(Collectors.toMap(Protos.Environment.Variable::getName, Protos.Environment.Variable::getValue));
        Assert.assertEquals(2, portEnvVarMap.size());
        Assert.assertTrue(portEnvVarMap.containsKey(expectedExplicitOverlayPortEnvvar));
        Assert.assertTrue(portEnvVarMap.containsKey(expextedDynamicOverlayPortEnvvar));
        Assert.assertTrue(portEnvVarMap.get(expectedExplicitOverlayPortEnvvar)
                .equals(expectedExplicitOverlayPort.toString()));
        Assert.assertTrue(portEnvVarMap.get(expextedDynamicOverlayPortEnvvar)
                .equals(expectedDynamicOverlayPort.toString()));
    }

    @Test
    public void testPortEnvvarOnHealthCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-healthcheck.yml");
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance))
                        .build();
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(offeredPorts);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                getPortSpec(podInstance),
                Collections.singleton(TestConstants.TASK_NAME),
                Optional.empty(),
                Optional.empty());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(1, outcome.getOfferRecommendations().size());
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().get().getType());
        Protos.Resource resource = recommendation.getOperation().get().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilders().stream().findFirst().get();
        boolean portInTaskEnv = false;
        for (int i = 0; i < taskBuilder.getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "PORT_TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInTaskEnv = true;
            }
        }
        Assert.assertTrue(portInTaskEnv);
        boolean portInHealthEnv = false;
        for (int i = 0; i < taskBuilder.getHealthCheck().getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getHealthCheck().getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "PORT_TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInHealthEnv = true;
            }
        }
        Assert.assertTrue(portInHealthEnv);
    }

    @Test
    public void testHealthCheckPortEnvvarIsCorrectOnOverlay() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-healthcheck-overlay.yml");
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance))
                        .build();
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                getPortSpec(podInstance),
                Collections.singleton(TestConstants.TASK_NAME),
                Optional.empty(),
                Optional.empty());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilders().stream().findFirst().get();
        Assert.assertTrue(taskBuilder.getCommand().getEnvironment().getVariablesList().stream()
                .filter(variable -> variable.getName().equals("PORT_TEST_PORT") && variable.getValue().equals("10000"))
                .count() == 1);
        Assert.assertTrue(taskBuilder.getHealthCheck().getCommand().getEnvironment().getVariablesList().stream()
                .filter(variable -> variable.getName().equals("PORT_TEST_PORT") && variable.getValue().equals("10000"))
                .count() == 1);
    }

    @Test
    public void testReadinessCheckPortEnvvarIsCorrectOnOverlay() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-readinesscheck-overlay.yml");
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance))
                        .build();
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(offeredPorts);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                getPortSpec(podInstance),
                Collections.singleton(TestConstants.TASK_NAME),
                Optional.empty(),
                Optional.empty());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilders().stream().findFirst().get();
        Assert.assertTrue(taskBuilder.getCommand().getEnvironment().getVariablesList().stream()
                .filter(variable -> variable.getName().equals("PORT_TEST_PORT") && variable.getValue().equals("10000"))
                .count() == 1);

        Protos.CheckInfo readinessCheck = taskBuilder.hasCheck() ? taskBuilder.getCheck() : null;
        Assert.assertTrue(readinessCheck != null);
        Assert.assertTrue(readinessCheck.getCommand().getCommand().getEnvironment().getVariablesList().stream()
                .filter(variable -> variable.getName().equals("PORT_TEST_PORT") && variable.getValue().equals("10000"))
                .count() == 1);
    }

    @Test
    public void testPortEnvvarOnReadinessCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-readinesscheck.yml");
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance))
                        .build();
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                getPortSpec(podInstance),
                Collections.singleton(TestConstants.TASK_NAME),
                Optional.empty(),
                Optional.empty());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(1, outcome.getOfferRecommendations().size());
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().get().getType());
        Protos.Resource resource = recommendation.getOperation().get().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilders().stream().findFirst().get();
        boolean portInTaskEnv = false;
        for (int i = 0; i < taskBuilder.getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "PORT_TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInTaskEnv = true;
            }
        }
        Assert.assertTrue(portInTaskEnv);
        boolean portInHealthEnv = false;
        Protos.CheckInfo readinessCheck = taskBuilder.getCheck();
        for (int i = 0; i < readinessCheck.getCommand().getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = readinessCheck.getCommand()
                    .getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "PORT_TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInHealthEnv = true;
            }
        }
        Assert.assertTrue(portInHealthEnv);
    }

    @Test
    public void testDynamicPortNotStickyAfterReplacement() throws Exception {
        // The initial dynamic port should be the min of the available range.
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10050);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        PortSpec.Builder builder = PortSpec.newBuilder()
                .envKey("PORT_TEST")
                .portName("TEST")
                .visibility(TestConstants.PORT_VISIBILITY)
                .networkNames(Collections.emptyList());
        builder
                .value(getPort(0))
                .role(TestConstants.ROLE)
                .preReservedRole(Constants.ANY_ROLE)
                .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                PodTestUtils.getTemplateUrlFactory(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Collections.emptyList(),
                TestConstants.FRAMEWORK_ID,
                Collections.emptyMap());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec,
                Collections.singleton(TestConstants.TASK_NAME),
                Optional.empty(),
                Optional.empty());

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(true, outcome.isPassing());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(), "TEST", 10000);

        // In a restart, we want port stickiness. It should fail if the original dynamic port is not
        // available in the offer.
        Protos.TaskInfo.Builder currentTaskBuilder = podInfoBuilder.getTaskBuilders().stream().findFirst().get();

        podInfoBuilder = new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                PodTestUtils.getTemplateUrlFactory(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Collections.singleton(currentTaskBuilder.build()),
                TestConstants.FRAMEWORK_ID,
                Collections.emptyMap());

        // Omit 10,000 the expected port.
        offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedPorts(10001, 10050));
        mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(false, outcome.isPassing());

        // In permanent replacement, the previous dynamic port should be discarded, so an offer
        // without that port should be valid.
        currentTaskBuilder.setLabels(new TaskLabelWriter(currentTaskBuilder).setPermanentlyFailed().toProto());

        podInfoBuilder = new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                PodTestUtils.getTemplateUrlFactory(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Collections.singleton(currentTaskBuilder.build()),
                TestConstants.FRAMEWORK_ID,
                Collections.emptyMap());

        mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(true, outcome.isPassing());
    }

    @Test
    public void testDynamicPortWithPrereservedRoleFails() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10050);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        PortSpec.Builder builder = PortSpec.newBuilder()
            .envKey("PORT_TEST")
            .portName("TEST")
            .visibility(TestConstants.PORT_VISIBILITY)
            .networkNames(Collections.emptyList());
        builder
            .value(getPort(0))
            .role(TestConstants.ROLE)
            .preReservedRole("slave_public")
            .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceWithPrereservedRole(portSpec);

        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
            podInstanceRequirement,
            TestConstants.SERVICE_NAME,
            UUID.randomUUID(),
            PodTestUtils.getTemplateUrlFactory(),
            SchedulerConfigTestUtils.getTestSchedulerConfig(),
            Collections.emptyList(),
            TestConstants.FRAMEWORK_ID,
            Collections.emptyMap());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
            portSpec,
            Collections.singleton(TestConstants.TASK_NAME),
            Optional.empty(),
            Optional.empty());

        //this should fail since no slave_public port is present in the offer
        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(false, outcome.isPassing());
    }

    @Test
    public void testDynamicPortWithPrereservedRole() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getPrereservedPort(23, 5050, "slave_public");
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(offeredPorts), "slave_public");
        PortSpec.Builder builder = PortSpec.newBuilder()
            .envKey("PORT_TEST")
            .portName("TEST")
            .visibility(TestConstants.PORT_VISIBILITY)
            .networkNames(Collections.emptyList());
        builder
            .value(getPort(0))
            .role(TestConstants.ROLE)
            .preReservedRole("slave_public")
            .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceWithPrereservedRole(portSpec);

        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
            podInstanceRequirement,
            TestConstants.SERVICE_NAME,
            UUID.randomUUID(),
            PodTestUtils.getTemplateUrlFactory(),
            SchedulerConfigTestUtils.getTestSchedulerConfig(),
            Collections.emptyList(),
            TestConstants.FRAMEWORK_ID,
            Collections.emptyMap());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
            portSpec,
            Collections.singleton(TestConstants.TASK_NAME),
            Optional.empty(),
            Optional.empty());

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of("slave_public"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(true, outcome.isPassing());
    }

    @Test
    public void testDynamicPortWithRangesPasses() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getPrereservedPort(23, 5050, "slave_public");
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(offeredPorts), "slave_public");

        //port 25 will match
        RangeSpec spec = new RangeSpec(25, 600);

        PortSpec.Builder builder = PortSpec.newBuilder()
            .envKey("PORT_TEST")
            .portName("TEST")
            .ranges(Arrays.asList(spec))
            .visibility(TestConstants.PORT_VISIBILITY)
            .networkNames(Collections.emptyList());
        builder
            .value(getPort(0))
            .role(TestConstants.ROLE)
            .preReservedRole("slave_public")
            .principal(TestConstants.PRINCIPAL);

        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceWithPrereservedRole(portSpec);

        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
            podInstanceRequirement,
            TestConstants.SERVICE_NAME,
            UUID.randomUUID(),
            PodTestUtils.getTemplateUrlFactory(),
            SchedulerConfigTestUtils.getTestSchedulerConfig(),
            Collections.emptyList(),
            TestConstants.FRAMEWORK_ID,
            Collections.emptyMap());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
            portSpec,
            Collections.singleton(TestConstants.TASK_NAME),
            Optional.empty(),
            Optional.empty());

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of("slave_public"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(true, outcome.isPassing());
    }

    @Test
    public void testDynamicPortWithRangesFails() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getPrereservedPort(23, 5050, "slave_public");
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(offeredPorts), "slave_public");

        //no port in offer in range
        RangeSpec spec = new RangeSpec(6000, 8000);

        PortSpec.Builder builder = PortSpec.newBuilder()
            .envKey("PORT_TEST")
            .portName("TEST")
            .ranges(Arrays.asList(spec))
            .visibility(TestConstants.PORT_VISIBILITY)
            .networkNames(Collections.emptyList());
        builder
            .value(getPort(0))
            .role(TestConstants.ROLE)
            .preReservedRole("slave_public")
            .principal(TestConstants.PRINCIPAL);

        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceWithPrereservedRole(portSpec);

        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
            podInstanceRequirement,
            TestConstants.SERVICE_NAME,
            UUID.randomUUID(),
            PodTestUtils.getTemplateUrlFactory(),
            SchedulerConfigTestUtils.getTestSchedulerConfig(),
            Collections.emptyList(),
            TestConstants.FRAMEWORK_ID,
            Collections.emptyMap());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
            portSpec,
            Collections.singleton(TestConstants.TASK_NAME),
            Optional.empty(),
            Optional.empty());

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of("slave_public"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(false, outcome.isPassing());
    }

    @Test
    public void testDynamicPortWithMultipleRangesFails() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getPrereservedPort(23, 5050, "slave_public");
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(offeredPorts), "slave_public");

        //offer contains a value in neither range
        RangeSpec spec1 = new RangeSpec(6000, 8000);
        RangeSpec spec2 = new RangeSpec(2, 21);

        PortSpec.Builder builder = PortSpec.newBuilder()
            .envKey("PORT_TEST")
            .portName("TEST")
            .ranges(Arrays.asList(spec1, spec2))
            .visibility(TestConstants.PORT_VISIBILITY)
            .networkNames(Collections.emptyList());
        builder
            .value(getPort(0))
            .role(TestConstants.ROLE)
            .preReservedRole("slave_public")
            .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceWithPrereservedRole(portSpec);

        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
            podInstanceRequirement,
            TestConstants.SERVICE_NAME,
            UUID.randomUUID(),
            PodTestUtils.getTemplateUrlFactory(),
            SchedulerConfigTestUtils.getTestSchedulerConfig(),
            Collections.emptyList(),
            TestConstants.FRAMEWORK_ID,
            Collections.emptyMap());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
            portSpec,
            Collections.singleton(TestConstants.TASK_NAME),
            Optional.empty(),
            Optional.empty());

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of("slave_public"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(false, outcome.isPassing());
    }

    @Test
    public void testDynamicPortWithUnboundedRanges() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getPrereservedPort(3000, 5050, "slave_public");
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(offeredPorts), "slave_public");

        //unbounded upper range from 1024 will match
        RangeSpec spec1 = new RangeSpec(1024, null);

        PortSpec.Builder builder = PortSpec.newBuilder()
            .envKey("PORT_TEST")
            .portName("TEST")
            .ranges(Arrays.asList(spec1))
            .visibility(TestConstants.PORT_VISIBILITY)
            .networkNames(Collections.emptyList());
        builder
            .value(getPort(0))
            .role(TestConstants.ROLE)
            .preReservedRole("slave_public")
            .principal(TestConstants.PRINCIPAL);
        PortSpec portSpec = builder.build();

        PodInstanceRequirement podInstanceRequirement = getPodInstanceWithPrereservedRole(portSpec);

        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
            podInstanceRequirement,
            TestConstants.SERVICE_NAME,
            UUID.randomUUID(),
            PodTestUtils.getTemplateUrlFactory(),
            SchedulerConfigTestUtils.getTestSchedulerConfig(),
            Collections.emptyList(),
            TestConstants.FRAMEWORK_ID,
            Collections.emptyMap());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
            portSpec,
            Collections.singleton(TestConstants.TASK_NAME),
            Optional.empty(),
            Optional.empty());

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of("slave_public"));
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertEquals(true, outcome.isPassing());
    }
}
