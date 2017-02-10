package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PortEvaluationStageTest {
    @ClassRule
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    @Test
    public void testReserveDynamicPort() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage =
                new PortEvaluationStage(desiredPorts, TestConstants.TASK_NAME, "test-port", 0);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "TEST_PORT");
        Assert.assertEquals(variable.getValue(), "10000");
    }

    @Test
    public void testReserveKnownPort() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 10000, 10000);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage =
                new PortEvaluationStage(desiredPorts, TestConstants.TASK_NAME, "test-port", 10000);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "TEST_PORT");
        Assert.assertEquals(variable.getValue(), "10000");
    }

    @Test
    public void testReserveKnownPortFails() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 1111, 1111);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts, TestConstants.TASK_NAME, "test-port", 10001);
        EvaluationOutcome outcome =
                portEvaluationStage.evaluate(new MesosResourcePool(offer), new PodInfoBuilder(offerRequirement));
        Assert.assertFalse(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
    }

    @Test
    public void testPortEnvCharConversion() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(5000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage =
                new PortEvaluationStage(desiredPorts, TestConstants.TASK_NAME, "port?test.port", 0);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());
        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                5000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "PORT_TEST_PORT");
        Assert.assertEquals(variable.getValue(), "5000");
    }

    @Test
    public void testGetClaimedDynamicPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedPorts = ResourceTestUtils.getExpectedRanges("ports", 0, 0, resourceId);
        Protos.Resource offeredPorts = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(expectedPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        Protos.TaskInfo.Builder builder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        builder.getCommandBuilder().getEnvironmentBuilder().addVariablesBuilder()
                .setName("PORT_TEST_PORT")
                .setValue("10000");

        PortEvaluationStage portEvaluationStage =
                new PortEvaluationStage(expectedPorts, TestConstants.TASK_NAME, "port-test-port", 0);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(mesosResourcePool, podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
        Assert.assertEquals(0, mesosResourcePool.getReservedPool().size());
    }

    @Test
    public void testPortOnHealthCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-healthcheck.yml");
        StateStore stateStore = Mockito.mock(StateStore.class);
        DefaultOfferRequirementProvider provider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID());
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.create(podInstance, TaskUtils.getTaskNames(podInstance)));
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 10000, 10000);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        String taskName = "pod-type-0-" + TestConstants.TASK_NAME;
        PortEvaluationStage portEvaluationStage =
                new PortEvaluationStage(desiredPorts, taskName, "test-port", 10000);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
        boolean portInTaskEnv = false;
        for (int i = 0; i < taskBuilder.getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInTaskEnv = true;
            }
        }
        Assert.assertTrue(portInTaskEnv);
        boolean portInHealthEnv = false;
        for (int i = 0; i < taskBuilder.getHealthCheck().getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getHealthCheck().getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInHealthEnv = true;
            }
        }
        Assert.assertTrue(portInHealthEnv);
    }

    @Test
    public void testPortOnReadinessCheck() throws Exception {
        DefaultPodInstance podInstance = getPodInstance("valid-port-readinesscheck.yml");
        StateStore stateStore = Mockito.mock(StateStore.class);
        DefaultOfferRequirementProvider provider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID());
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.create(podInstance, TaskUtils.getTaskNames(podInstance)));
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 10000, 10000);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        String taskName = "pod-type-0-" + TestConstants.TASK_NAME;
        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(desiredPorts, taskName, "test-port", 10000);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(taskName);
        boolean portInTaskEnv = false;
        for (int i = 0; i < taskBuilder.getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInTaskEnv = true;
            }
        }
        Assert.assertTrue(portInTaskEnv);
        boolean portInHealthEnv = false;
        Optional<Protos.HealthCheck> readinessCheck = CommonTaskUtils.getReadinessCheck(taskBuilder.build());
        for (int i = 0; i < readinessCheck.get().getCommand().getEnvironment().getVariablesCount(); i++) {
            Protos.Environment.Variable variable = readinessCheck.get().getCommand().getEnvironment().getVariables(i);
            if (Objects.equals(variable.getName(), "TEST_PORT")) {
                Assert.assertEquals(variable.getValue(), "10000");
                portInHealthEnv = true;
            }
        }
        Assert.assertTrue(portInHealthEnv);
    }

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        DefaultServiceSpec serviceSpec = ServiceSpecTestUtils.getPodInstance(serviceSpecFileName);

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule((offer, offerRequirement, taskInfos) -> EvaluationOutcome.pass(this, "pass for test"))
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        return new DefaultPodInstance(serviceSpec.getPods().get(0), 0);
    }
}
