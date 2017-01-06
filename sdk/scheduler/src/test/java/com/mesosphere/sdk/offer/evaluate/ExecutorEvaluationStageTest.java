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
    @Test(expected = OfferEvaluationException.class)
    public void testRejectOfferWithoutExpectedExecutorId() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Protos.Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, resourceId);

        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        Optional<Protos.ExecutorInfo> execInfo = Optional.of(TaskTestUtils.getExecutorInfo(expectedExecutorMem));

        // Set incorrect ExecutorID
        execInfo = Optional.of(Protos.ExecutorInfo.newBuilder(execInfo.get())
                .setExecutorId(ExecutorUtils.toExecutorId(execInfo.get().getName()))
                .build());

        OfferRequirement offerRequirement =
                OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(taskInfo), execInfo);
        MesosResourcePool resources = new MesosResourcePool(
                OfferTestUtils.getOffer(Arrays.asList(expectedExecutorMem, expectedTaskCpu)));

        ExecutorEvaluationStage executorEvaluationStage = new ExecutorEvaluationStage(execInfo.get().getExecutorId());
        executorEvaluationStage.evaluate(resources, offerRequirement, new OfferRecommendationSlate());
    }

    @Test
    public void testAcceptOfferWithExpectedExecutorId() throws Exception {
        String taskResourceId = UUID.randomUUID().toString();
        String executorResourceId = UUID.randomUUID().toString();
        Protos.Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, taskResourceId);
        Protos.Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, executorResourceId);

        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        Protos.ExecutorInfo execInfo = TaskTestUtils.getExistingExecutorInfo(expectedExecutorMem);

        OfferRequirement offerRequirement =
                OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(taskInfo), Optional.of(execInfo));
        MesosResourcePool resources = new MesosResourcePool(
                OfferTestUtils.getOffer(
                        TestConstants.EXECUTOR_ID, Arrays.asList(expectedExecutorMem, expectedTaskCpu)));

        ExecutorEvaluationStage executorEvaluationStage = new ExecutorEvaluationStage(execInfo.getExecutorId());
        executorEvaluationStage.evaluate(resources, offerRequirement, new OfferRecommendationSlate());

        Assert.assertEquals(
                execInfo.getExecutorId(),
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getExecutorId());
    }
}
