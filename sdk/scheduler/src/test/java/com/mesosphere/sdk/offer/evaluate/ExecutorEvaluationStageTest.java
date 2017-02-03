package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.executor.ExecutorUtils;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class ExecutorEvaluationStageTest {

    @Test
    public void testRejectOfferWithoutExpectedExecutorId() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Protos.Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, resourceId);

        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        Protos.ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(expectedExecutorMem);

        // Set incorrect ExecutorID
        execInfo = Protos.ExecutorInfo.newBuilder(execInfo)
                .setExecutorId(ExecutorUtils.toExecutorId(execInfo.getName()))
                .build();

        OfferRequirement offerRequirement = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(new TaskRequirement(taskInfo)),
                Optional.of(ExecutorRequirement.create(execInfo)));
        MesosResourcePool resources = new MesosResourcePool(
                OfferTestUtils.getOffer(Arrays.asList(expectedExecutorMem, expectedTaskCpu)));

        ExecutorEvaluationStage executorEvaluationStage = new ExecutorEvaluationStage(execInfo.getExecutorId());
        EvaluationOutcome outcome =
                executorEvaluationStage.evaluate(resources, new PodInfoBuilder(offerRequirement));
        Assert.assertFalse(outcome.isPassing());
    }

    @Test
    public void testAcceptOfferWithExpectedExecutorId() throws Exception {
        String taskResourceId = UUID.randomUUID().toString();
        String executorResourceId = UUID.randomUUID().toString();
        Protos.Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, taskResourceId);
        Protos.Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, executorResourceId);

        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        Protos.ExecutorInfo execInfo = TaskTestUtils.getExistingExecutorInfo(expectedExecutorMem);

        OfferRequirement offerRequirement = OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(new TaskRequirement(taskInfo)),
                Optional.of(ExecutorRequirement.create(execInfo)));
        MesosResourcePool resources = new MesosResourcePool(
                OfferTestUtils.getOffer(
                        TestConstants.EXECUTOR_ID, Arrays.asList(expectedExecutorMem, expectedTaskCpu)));

        ExecutorEvaluationStage executorEvaluationStage = new ExecutorEvaluationStage(execInfo.getExecutorId());
        EvaluationOutcome outcome =
                executorEvaluationStage.evaluate(resources, new PodInfoBuilder(offerRequirement));
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(
                execInfo.getExecutorId(),
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getExecutorId());
    }
}
