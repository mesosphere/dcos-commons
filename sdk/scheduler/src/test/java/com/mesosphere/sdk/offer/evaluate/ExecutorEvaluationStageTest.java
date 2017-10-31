package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

public class ExecutorEvaluationStageTest extends OfferEvaluatorTestBase {
    @Test
    public void testRejectOfferWithoutExpectedExecutorId() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        // Record launch and RUNNING status
        String resourceId = getFirstResourceId(
                recordLaunchWithCompleteOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(1.1),
                        ResourceTestUtils.getUnreservedMem(256),
                        ResourceTestUtils.getUnreservedDisk(512)));
        String taskName = stateStore.fetchTaskNames().stream().findFirst().get();
        Protos.TaskInfo taskInfo = stateStore.fetchTask(taskName).get();
        stateStore.storeStatus(
                taskInfo.getName(),
                Protos.TaskStatus.newBuilder()
                .setState(Protos.TaskState.TASK_RUNNING)
                .setTaskId(taskInfo.getTaskId())
                .build());

        Protos.Resource expectedTaskCpu = ResourceTestUtils.getReservedCpus(1.0, resourceId);

        MesosResourcePool resources = new MesosResourcePool(
                OfferTestUtils.getCompleteOffer(Arrays.asList(expectedTaskCpu)),
                Optional.of(Constants.ANY_ROLE));

        ExecutorEvaluationStage executorEvaluationStage =
                new ExecutorEvaluationStage(Optional.of(taskInfo.getExecutor()));
        EvaluationOutcome outcome =
                executorEvaluationStage.evaluate(
                        resources,
                        new PodInfoBuilder(
                                podInstanceRequirement,
                                TestConstants.SERVICE_NAME,
                                UUID.randomUUID(),
                                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                                stateStore.fetchTasks(),
                                stateStore.fetchFrameworkId().get(),
                                true,
                                Collections.emptyMap()));
        Assert.assertFalse(outcome.isPassing());
    }

    @Test
    public void testRejectOfferWithoutExpectedExecutorIdCustomExecutor() throws Exception {
        useCustomExecutor();
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        // Record launch and RUNNING status
        String resourceId = getFirstResourceId(
                recordLaunchWithOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(1.0)));
        String taskName = stateStore.fetchTaskNames().stream().findFirst().get();
        Protos.TaskInfo taskInfo = stateStore.fetchTask(taskName).get();
        stateStore.storeStatus(
                taskInfo.getName(),
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .setTaskId(taskInfo.getTaskId())
                        .build());

        Protos.Resource expectedTaskCpu = ResourceTestUtils.getReservedCpus(1.0, resourceId);

        MesosResourcePool resources = new MesosResourcePool(
                OfferTestUtils.getOffer(Arrays.asList(expectedTaskCpu)),
                Optional.of(Constants.ANY_ROLE));

        ExecutorEvaluationStage executorEvaluationStage =
                new ExecutorEvaluationStage(Optional.of(taskInfo.getExecutor()));
        EvaluationOutcome outcome =
                executorEvaluationStage.evaluate(
                        resources,
                        new PodInfoBuilder(
                                podInstanceRequirement,
                                TestConstants.SERVICE_NAME,
                                UUID.randomUUID(),
                                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                                stateStore.fetchTasks(),
                                stateStore.fetchFrameworkId().get(),
                                false,
                                Collections.emptyMap()));
        Assert.assertFalse(outcome.isPassing());
    }

    @Test
    public void testAcceptOfferWithExpectedExecutorId() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        // Record launch and RUNNING status
        String resourceId = getFirstResourceId(
                recordLaunchWithCompleteOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(1.1),
                        ResourceTestUtils.getUnreservedMem(256),
                        ResourceTestUtils.getUnreservedDisk(512)));
        String taskName = stateStore.fetchTaskNames().stream().findFirst().get();
        Protos.TaskInfo taskInfo = stateStore.fetchTask(taskName).get();
        stateStore.storeStatus(
                taskInfo.getName(),
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .setTaskId(taskInfo.getTaskId())
                        .build());

        Protos.Resource expectedTaskCpu = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(expectedTaskCpu)).toBuilder()
                .addExecutorIds(taskInfo.getExecutor().getExecutorId())
                .build();
        MesosResourcePool resources = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        ExecutorEvaluationStage executorEvaluationStage =
                new ExecutorEvaluationStage(Optional.of(taskInfo.getExecutor()));
        PodInfoBuilder podInfoBuilder =
                new PodInfoBuilder(
                        podInstanceRequirement,
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        SchedulerConfigTestUtils.getTestSchedulerConfig(),
                        stateStore.fetchTasks(),
                        stateStore.fetchFrameworkId().get(),
                        true,
                        Collections.emptyMap());
        EvaluationOutcome outcome =
                executorEvaluationStage.evaluate(resources, podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.ExecutorID launchExecutorId = podInfoBuilder.getExecutorBuilder().get().getExecutorId();
        Assert.assertEquals(
                taskInfo.getExecutor().getExecutorId(),
                launchExecutorId);
    }

    @Test
    public void testAcceptOfferWithExpectedExecutorIdCustomExecutor() throws Exception {
        useCustomExecutor();
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        // Record launch and RUNNING status
        String resourceId = getFirstResourceId(
                recordLaunchWithOfferedResources(
                        podInstanceRequirement,
                        ResourceTestUtils.getUnreservedCpus(1.0)));
        String taskName = stateStore.fetchTaskNames().stream().findFirst().get();
        Protos.TaskInfo taskInfo = stateStore.fetchTask(taskName).get();
        stateStore.storeStatus(
                taskInfo.getName(),
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .setTaskId(taskInfo.getTaskId())
                        .build());

        Protos.Resource expectedTaskCpu = ResourceTestUtils.getReservedCpus(1.0, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(expectedTaskCpu)).toBuilder()
                .addExecutorIds(taskInfo.getExecutor().getExecutorId())
                .build();
        MesosResourcePool resources = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        ExecutorEvaluationStage executorEvaluationStage =
                new ExecutorEvaluationStage(Optional.of(taskInfo.getExecutor()));
        PodInfoBuilder podInfoBuilder =
                new PodInfoBuilder(
                        podInstanceRequirement,
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        SchedulerConfigTestUtils.getTestSchedulerConfig(),
                        stateStore.fetchTasks(),
                        stateStore.fetchFrameworkId().get(),
                        false,
                        Collections.emptyMap());
        EvaluationOutcome outcome =
                executorEvaluationStage.evaluate(resources, podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.ExecutorID launchExecutorId = podInfoBuilder.getExecutorBuilder().get().getExecutorId();
        Assert.assertEquals(
                taskInfo.getExecutor().getExecutorId(),
                launchExecutorId);
    }
}
