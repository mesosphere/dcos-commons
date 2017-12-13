package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LaunchEvaluationStageTest extends DefaultCapabilitiesTestSuite {
    private LaunchEvaluationStage stage;
    private Protos.Offer offer;
    private PodInfoBuilder podInfoBuilder;

    @Before
    public void beforeEach() throws InvalidRequirementException {
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedCpus(2.0);

        stage = new LaunchEvaluationStage(TestConstants.TASK_NAME);
        offer = OfferTestUtils.getOffer(offeredResource);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        podInfoBuilder = new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Collections.emptyList(),
                TestConstants.FRAMEWORK_ID,
                true,
                Collections.emptyMap());
    }

    @Test
    public void isPassing() {
        EvaluationOutcome outcome = stage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
    }

    @Test
    public void labelsAreCorrect() {
        stage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);

        // labels are sorted alphabetically (see LabelUtils):
        Protos.Label label = taskBuilder.getLabels().getLabels(0);
        Assert.assertEquals("goal_state", label.getKey());
        Assert.assertEquals(GoalState.RUNNING.name(), label.getValue());

        label = taskBuilder.getLabels().getLabels(1);
        Assert.assertEquals("index", label.getKey());
        Assert.assertEquals(Integer.toString(TestConstants.TASK_INDEX), label.getValue());

        label = taskBuilder.getLabels().getLabels(2);
        Assert.assertEquals("offer_attributes", label.getKey());
        Assert.assertEquals("", label.getValue());

        label = taskBuilder.getLabels().getLabels(3);
        Assert.assertEquals("offer_hostname", label.getKey());
        Assert.assertEquals(TestConstants.HOSTNAME, label.getValue());

        label = taskBuilder.getLabels().getLabels(4);
        Assert.assertEquals("target_configuration", label.getKey());
        Assert.assertEquals(36, label.getValue().length());

        label = taskBuilder.getLabels().getLabels(5);
        Assert.assertEquals(label.getKey(), "task_type");
        Assert.assertEquals(TestConstants.POD_TYPE, label.getValue());
    }

    @Test
    public void regionAndZoneInjected() {
        offer = offer.toBuilder()
                .setDomain(TestConstants.LOCAL_DOMAIN_INFO)
                .build();
        stage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);

        Map<String, String> env = EnvUtils.toMap(taskBuilder.getCommand().getEnvironment());
        Assert.assertEquals(TestConstants.LOCAL_REGION, env.get(EnvConstants.REGION_TASKENV));
        Assert.assertEquals(TestConstants.ZONE, env.get(EnvConstants.ZONE_TASKENV));
    }

    @Test
    public void regionAndZoneNotInjected() {
        stage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);

        Map<String, String> env = EnvUtils.toMap(taskBuilder.getCommand().getEnvironment());
        Assert.assertNull(env.get(EnvConstants.REGION_TASKENV));
        Assert.assertNull(env.get(EnvConstants.ZONE_TASKENV));
    }
}
