package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

public class PortEvaluationStageTest extends DefaultCapabilitiesTestSuite {
    private static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

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
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                Collections.emptyList(),
                TestConstants.FRAMEWORK_ID);
    }

    private PodInstanceRequirement getPodInstanceRequirement(PortSpec... portSpecs) {
        // Build Pod

        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .id("resourceSet")
                .cpus(1.0)
                .addResource(Arrays.asList(portSpecs))
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

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        DefaultServiceSpec serviceSpec = ServiceSpecTestUtils.getPodInstance(serviceSpecFileName, flags);

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule((offer, offerRequirement, taskInfos) -> EvaluationOutcome.pass(this, null, "pass for test"))
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        return new DefaultPodInstance(serviceSpec.getPods().get(0), 0);
    }

    private List<String> getOverlayNetworkNames() {
        return new ArrayList<>(Arrays.asList(DcosConstants.DEFAULT_OVERLAY_NETWORK));
    }

    private PortSpec getPortSpec(PodInstance podInstance) {
        return (PortSpec) podInstance.getPod().getTasks().get(0).getResourceSet().getResources().stream()
                .filter(resourceSpec -> resourceSpec instanceof PortSpec)
                .findFirst()
                .get();
    }

    private void checkDiscoveryInfo(Protos.DiscoveryInfo discoveryInfo,
                                       String expectedPortName,
                                       Protos.DiscoveryInfo.Visibility expectedVisibility,
                                       long expectedPort) {
        Assert.assertTrue(String.format("Incorrect visibility got %s should be %s",
                discoveryInfo.getVisibility().toString(), expectedVisibility.toString()),
                discoveryInfo.getVisibility().equals(expectedVisibility));
        List<Protos.Port> ports = discoveryInfo.getPorts().getPortsList().stream()
                .filter(p -> p.getName().equals(expectedPortName))
                .collect(Collectors.toList());
        Assert.assertTrue(String.format("Didn't find port with name %s, got ports %s", expectedPortName,
                ports.toString()), ports.size() == 1);
        Protos.Port port = ports.get(0);
        Assert.assertTrue(String.format("Port %s has incorrect number got %d should be %d",
                port.toString(), port.getNumber(), expectedPort), port.getNumber() == expectedPort);
        Assert.assertTrue(discoveryInfo.getVisibility().equals(expectedVisibility));
    }

    @Test
    public void testPortResourceIsIgnoredOnOverlay() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        Integer requestedPort = 80;  // request a port that's not available in the offer.
        String expectedPortEnvVar = "PORT_TEST_IGNORED";
        String expectedPortName = "overlay-port-name";
        PortSpec portSpec = new PortSpec(
                getPort(requestedPort),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL,
                "port?test.ignored",
                expectedPortName,
                getOverlayNetworkNames());
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec, TestConstants.TASK_NAME, Optional.empty(), portSpec.getPortName());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(),
               expectedPortName, Protos.DiscoveryInfo.Visibility.FRAMEWORK, 80);
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
        PortSpec portSpec = new PortSpec(
                getPort(0),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL,
                "port?test.dynamic.overlay",
                expectedPortName,
                getOverlayNetworkNames());
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec, TestConstants.TASK_NAME, Optional.empty(), portSpec.getPortName());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(),
                expectedPortName, Protos.DiscoveryInfo.Visibility.FRAMEWORK, expectedDynamicallyAssignedPort);
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
    public void testDynamicPortResourceOnOverlayWithRequestedPortToo() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        Integer expectedExplicitOverlayPort = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START;
        Integer expectedDynamicOverlayPort = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START + 1;
        String expectedExplicitOverlayPortEnvvar = "PORT_TEST_EXPLICIT";
        String expextedDynamicOverlayPortEnvvar = "PORT_TEST_DYNAMIC";
        String expectedExplicitPortName = "explicit-port";
        String expectedDynamicPortName = "dynamic-port";
        PortSpec portSpec = new PortSpec(
                getPort(DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL,
                "port?test.explicit",
                expectedExplicitPortName,
                getOverlayNetworkNames());
        PortSpec dynamPortSpec = new PortSpec(
                getPort(0),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL,
                "port?test.dynamic",
                expectedDynamicPortName,
                getOverlayNetworkNames());
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec, dynamPortSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        Assert.assertTrue(String.format("podInfoBuilder has incorrect number of pre-assigned overlay ports " +
                        "should be 1, got %s", podInfoBuilder.getAssignedOverlayPorts().size()),
                podInfoBuilder.getAssignedOverlayPorts().size() == 1);
        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        PortEvaluationStage portEvaluationStage_ = new PortEvaluationStage(
                portSpec, TestConstants.TASK_NAME, Optional.empty(), portSpec.getPortName());
        EvaluationOutcome outcome0 = portEvaluationStage_.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertTrue(outcome0.isPassing());
        Assert.assertEquals(0, outcome0.getOfferRecommendations().size());
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                dynamPortSpec, TestConstants.TASK_NAME, Optional.empty(), dynamPortSpec.getPortName());
        EvaluationOutcome outcome1 = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertTrue(outcome1.isPassing());
        Assert.assertEquals(0, outcome1.getOfferRecommendations().size());
        Assert.assertTrue(String.format("podInfoBuilder has incorrect number of assigned overlay ports, " +
                        "should be 2 got %s", podInfoBuilder.getAssignedOverlayPorts().size()),
                podInfoBuilder.getAssignedOverlayPorts().size() == 2);
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(),
                expectedExplicitPortName, Protos.DiscoveryInfo.Visibility.FRAMEWORK, expectedExplicitOverlayPort);
        checkDiscoveryInfo(taskBuilder.getDiscovery(),
                expectedDynamicPortName, Protos.DiscoveryInfo.Visibility.FRAMEWORK, expectedDynamicOverlayPort);
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
    public void testPortEnvCharConversion() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(5000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        int expectedPortNumber = 5000;
        String expectedPortName = "dyn-port-name";
        PortSpec portSpec = new PortSpec(
                getPort(expectedPortNumber),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL,
                "port?test.port",
                expectedPortName,
                Collections.emptyList());
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec,
                TestConstants.TASK_NAME,
                Optional.empty(), portSpec.getPortName());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());
        Assert.assertEquals(1, podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getResourcesCount());
        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                5000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        checkDiscoveryInfo(taskBuilder.getDiscovery(),
                expectedPortName, Protos.DiscoveryInfo.Visibility.FRAMEWORK, expectedPortNumber);
        Assert.assertTrue(taskBuilder.getCommand().getEnvironment().getVariablesList().stream()
                .filter(v -> v.getName().equals("PORT_TEST_PORT")
                        && v.getValue().equals(String.valueOf(expectedPortNumber)))
                .count() == 1);
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
                TestConstants.TASK_NAME,
                Optional.empty(), getPortSpec(podInstance).getPortName());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(1, outcome.getOfferRecommendations().size());
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());
        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
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
                TestConstants.TASK_NAME,
                Optional.empty(), getPortSpec(podInstance).getPortName());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
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
                TestConstants.TASK_NAME,
                Optional.empty(), getPortSpec(podInstance).getPortName());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
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
                TestConstants.TASK_NAME,
                Optional.empty(), getPortSpec(podInstance).getPortName());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(1, outcome.getOfferRecommendations().size());
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());
        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
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
}
