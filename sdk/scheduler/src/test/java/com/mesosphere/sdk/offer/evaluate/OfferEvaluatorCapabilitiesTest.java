package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.http.endpoints.ArtifactResource;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.junit.Test;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OfferEvaluatorCapabilitiesTest extends DefaultCapabilitiesTestSuite  {

    private static Protos.Offer offerWithAgent(String agentId, Protos.Resource resource) {
        Protos.Offer.Builder o = OfferTestUtils.getCompleteOffer(resource).toBuilder();
        o.getSlaveIdBuilder().setValue(agentId);

        return o.build();
    }


    @Test
    public void testOfferAllCapabilites() throws Exception {

        String agent = "test-agent";
        Protos.Resource offered = ResourceTestUtils.getUnreservedCpus(1.0);
        Protos.Offer offer = offerWithAgent(agent, offered);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        LinuxCapabilitiesEvaluationStage capabilityEvaluationStage = new LinuxCapabilitiesEvaluationStage(TestConstants.TASK_NAME, Optional.empty());
        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        List<String> taskNames = TaskUtils.getTaskNames(podInstance);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement
                .newBuilder(podInstance, taskNames)
                .build();

        //default pod should have ALL capabilities set

        EvaluationOutcome outcome = capabilityEvaluationStage.evaluate(
                mesosResourcePool,
                new PodInfoBuilder(
                        podInstanceRequirement,
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        ArtifactResource.getUrlFactory(TestConstants.SERVICE_NAME),
                        SchedulerConfigTestUtils.getTestSchedulerConfig(),
                        Collections.emptyList(),
                        TestConstants.FRAMEWORK_ID,
                        Collections.emptyMap()));


        Assert.isTrue(outcome.isPassing());
    }

    @Test
    public void testOfferNoCapabilites() throws Exception {

        String agent = "test-agent";
        Protos.Resource offered = ResourceTestUtils.getUnreservedCpus(1.0);
        Protos.Offer offer = offerWithAgent(agent, offered);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        LinuxCapabilitiesEvaluationStage capabilityEvaluationStage = new LinuxCapabilitiesEvaluationStage(TestConstants.TASK_NAME, Optional.empty());
        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        List<String> taskNames = TaskUtils.getTaskNames(podInstance);

        podInstance.getPod().getCapabilities().clear();

        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement
                .newBuilder(podInstance, taskNames)
                .build();

        EvaluationOutcome outcome = capabilityEvaluationStage.evaluate(
                mesosResourcePool,
                new PodInfoBuilder(
                        podInstanceRequirement,
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        ArtifactResource.getUrlFactory(TestConstants.SERVICE_NAME),
                        SchedulerConfigTestUtils.getTestSchedulerConfig(),
                        Collections.emptyList(),
                        TestConstants.FRAMEWORK_ID,
                        Collections.emptyMap()));


        Assert.isTrue(outcome.isPassing());
    }

    @Test
    public void testOfferPartialCapabilites() throws Exception {

        String agent = "test-agent";
        Protos.Resource offered = ResourceTestUtils.getUnreservedCpus(1.0);
        Protos.Offer offer = offerWithAgent(agent, offered);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        LinuxCapabilitiesEvaluationStage capabilityEvaluationStage = new LinuxCapabilitiesEvaluationStage(TestConstants.TASK_NAME, Optional.empty());
        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        List<String> taskNames = TaskUtils.getTaskNames(podInstance);

        podInstance.getPod().getCapabilities().clear();

        //TODO: fix this test to request a subset of capabilities
        //podInstance.getPod().getCapabilities().toArray()

        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement
                .newBuilder(podInstance, taskNames)
                .build();

        EvaluationOutcome outcome = capabilityEvaluationStage.evaluate(
                mesosResourcePool,
                new PodInfoBuilder(
                        podInstanceRequirement,
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        ArtifactResource.getUrlFactory(TestConstants.SERVICE_NAME),
                        SchedulerConfigTestUtils.getTestSchedulerConfig(),
                        Collections.emptyList(),
                        TestConstants.FRAMEWORK_ID,
                        Collections.emptyMap()));


        Assert.isTrue(outcome.isPassing());
    }






}
