package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.TaskUtils;
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

public class PortEvaluationStageTest {
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
                Collections.emptyList());
    }

    private PodInstanceRequirement getPodInstanceRequirement(PortSpec... portSpecs) {
        // Build Pod

        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                .id("resourceSet")
                .cpus(1.0)
                .addResource(Arrays.asList(portSpecs))
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

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        DefaultServiceSpec serviceSpec = ServiceSpecTestUtils.getPodInstance(serviceSpecFileName, flags);

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule((offer, offerRequirement, taskInfos) -> EvaluationOutcome.pass(this, "pass for test"))
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        return new DefaultPodInstance(serviceSpec.getPods().get(0), 0);
    }

    private List<String> getOverlayNetworkNames() {
        return new ArrayList<>(Arrays.asList(DcosConstants.DEFAULT_OVERLAY_NETWORK));
    }

    @Test
    public void testPortResourceIsIgnoredOnOverlay() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);
        Integer requestedPort = 80;  // request a port that's not available in the offer.
        String expectedPortEnvVar = "PORT_TEST_IGNORED";
        PortSpec portSpec = new PortSpec(
                getPort(requestedPort),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "port?test.ignored",
                "overlay-port-name",
                getOverlayNetworkNames());
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec, TestConstants.TASK_NAME, Optional.empty());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
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
        PortSpec portSpec = new PortSpec(
                getPort(0),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "port?test.dynamic.overlay",
                "dyn-port-name",
                getOverlayNetworkNames());
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec, TestConstants.TASK_NAME, Optional.empty());
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
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

        PortSpec portSpec = new PortSpec(
                getPort(DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "port?test.port",
                "conflicts-with-dynamic",
                getOverlayNetworkNames());

        PortSpec dynamPortSpec = new PortSpec(
                getPort(0),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "port?test.port",
                "conflicts-with-explicit",
                getOverlayNetworkNames());

        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec, dynamPortSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);

        Assert.assertTrue(String.format("podInfoBuilder has incorrect number of pre-assigned overlay ports " +
                        "should be 1, got %s", podInfoBuilder.getAssignedOverlayPorts().size()),
                podInfoBuilder.getAssignedOverlayPorts().size() == 1);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                dynamPortSpec, TestConstants.TASK_NAME, Optional.empty());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(String.format("podInfoBuilder has incorrect number of assigned overlay ports, " +
                        "should be 2 got %s", podInfoBuilder.getAssignedOverlayPorts().size()),
                podInfoBuilder.getAssignedOverlayPorts().size() == 2);
        Assert.assertTrue(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Assert.assertEquals(0, taskBuilder.getResourcesCount());
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "TEST_PORT");
        Integer expected = DcosConstants.OVERLAY_DYNAMIC_PORT_RANGE_START + 1;
        Assert.assertEquals(variable.getValue(), expected.toString());
    }


    @Test
    public void testPortEnvCharConversion() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(5000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        PortSpec portSpec = new PortSpec(
                getPort(0),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                "port?test.port",
                "dyn-port-name",
                Collections.emptyList());
        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(portSpec);
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                portSpec,
                TestConstants.TASK_NAME,
                Optional.empty());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());
        Assert.assertEquals(1, podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getResourcesCount());
        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                5000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(2);
        Assert.assertEquals(variable.getName(), "PORT_TEST_PORT");
        Assert.assertEquals(variable.getValue(), "5000");
    }

    private PortSpec getPortSpec(PodInstance podInstance) {
        return (PortSpec) podInstance.getPod().getTasks().get(0).getResourceSet().getResources().stream()
                .filter(resourceSpec -> resourceSpec instanceof PortSpec)
                .findFirst()
                .get();
    }

    @Test
    public void testPortOnHealthCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-healthcheck.yml");
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance))
                        .build();
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);

        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        String taskName = "pod-type-0-" + TestConstants.TASK_NAME;
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                getPortSpec(podInstance),
                TestConstants.TASK_NAME,
                Optional.empty());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
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
    public void testPortOnReadinessCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-readinesscheck.yml");
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance))
                        .build();
        PodInfoBuilder podInfoBuilder = getPodInfoBuilder(podInstanceRequirement);

        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        String taskName = "pod-type-0-" + TestConstants.TASK_NAME;
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                getPortSpec(podInstance),
                TestConstants.TASK_NAME,
                Optional.empty());

        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
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
        Optional<Protos.HealthCheck> readinessCheck = OfferRequirementTestUtils.getReadinessCheck(taskBuilder.build());
        for (int i = 0; i < readinessCheck.get().getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = readinessCheck.get().getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "PORT_TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInHealthEnv = true;
            }
        }
        Assert.assertTrue(portInHealthEnv);
    }
}
