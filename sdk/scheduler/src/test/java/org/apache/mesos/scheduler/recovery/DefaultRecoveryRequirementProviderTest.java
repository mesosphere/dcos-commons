package org.apache.mesos.scheduler.recovery;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.DefaultTaskConfigRouter;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.*;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.plan.DefaultPodInstance;
import org.apache.mesos.scheduler.plan.Step;
import org.apache.mesos.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.monitor.NeverFailureMonitor;
import org.apache.mesos.specification.*;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testing.CuratorTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Created by gabriel on 11/15/16.
 */
public class DefaultRecoveryRequirementProviderTest {
    private static final TaskSpec taskSpec0 =
            TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID + 0);
    private static final TaskSpec taskSpec1 =
            TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID + 1);
    private static final PodSpec POD_SPEC =
            TestPodFactory.getPodSpec(TestConstants.POD_TYPE, 1, Arrays.asList(taskSpec0, taskSpec1));

    private static final PodInstance POD_INSTANCE = new DefaultPodInstance(POD_SPEC, 0);

    private static final ServiceSpec serviceSpec =
            DefaultServiceSpec.newBuilder()
                    .name(TestConstants.SERVICE_NAME)
                    .role(TestConstants.ROLE)
                    .principal(TestConstants.PRINCIPAL)
                    .apiPort(0)
                    .zookeeperConnection("foo.bar.com")
                    .pods(Arrays.asList(POD_SPEC))
                    .build();

    private static TestingServer testingServer;
    private EnvironmentVariables environmentVariables;

    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;
    private DefaultRecoveryRequirementProvider recoveryRequirementProvider;
    private OfferRequirementProvider offerRequirementProvider;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testingServer);

        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");

        stateStore = new CuratorStateStore(
                "test-framework-name",
                testingServer.getConnectString());

        configStore = DefaultScheduler.createConfigStore(
                serviceSpec,
                testingServer.getConnectString(),
                Collections.emptyList());

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        offerRequirementProvider = new DefaultOfferRequirementProvider(
                new DefaultTaskConfigRouter(),
                stateStore,
                configId);
        recoveryRequirementProvider = new DefaultRecoveryRequirementProvider(
                offerRequirementProvider,
                configStore,
                stateStore);

    }

    @Test
    public void testFailedPodWithMultipleTasks() throws InvalidRequirementException {
        List<String> taskNames = POD_SPEC.getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        OfferRequirement offerRequirement = offerRequirementProvider.getNewOfferRequirement(POD_INSTANCE, taskNames);
        List<TaskInfo> taskInfos = offerRequirement.getTaskRequirements().stream()
                .map(taskRequirement -> taskRequirement.getTaskInfo())
                .collect(Collectors.toList());
        Assert.assertEquals(2, taskInfos.size());

        taskInfos = taskInfos.stream()
                .map(taskInfo -> TaskUtils.setType(taskInfo.toBuilder(), POD_SPEC.getType()).build())
                .map(taskInfo -> TaskUtils.setIndex(taskInfo.toBuilder(), POD_INSTANCE.getIndex()).build())
                .collect(Collectors.toList());

        stateStore.storeTasks(taskInfos);

        List<RecoveryRequirement> recoveryRequirements =
                recoveryRequirementProvider.getTransientRecoveryRequirements(taskInfos);

        Assert.assertEquals(1, recoveryRequirements.size());

        RecoveryRequirement recoveryRequirement = recoveryRequirements.get(0);
        Assert.assertEquals(2, recoveryRequirement.getOfferRequirement().getTaskRequirements().size());
        Assert.assertTrue(recoveryRequirement.getOfferRequirement().getExecutorRequirementOptional().isPresent());
    }

    @Test
    public void testRecoveryPlanManagerTransientlyFailedPod() throws InvalidRequirementException {
        List<String> taskNames = POD_SPEC.getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        OfferRequirement offerRequirement = offerRequirementProvider.getNewOfferRequirement(POD_INSTANCE, taskNames);
        List<TaskInfo> taskInfos = offerRequirement.getTaskRequirements().stream()
                .map(taskRequirement -> taskRequirement.getTaskInfo())
                .collect(Collectors.toList());
        Assert.assertEquals(2, taskInfos.size());

        taskInfos = taskInfos.stream()
                .map(taskInfo -> TaskUtils.setType(taskInfo.toBuilder(), POD_SPEC.getType()).build())
                .map(taskInfo -> TaskUtils.setIndex(taskInfo.toBuilder(), POD_INSTANCE.getIndex()).build())
                .collect(Collectors.toList());

        stateStore.storeTasks(taskInfos);

        final Protos.TaskStatus failedStatus0 = TaskTestUtils.generateStatus(
                taskInfos.get(0).getTaskId(),
                Protos.TaskState.TASK_FAILED);
        final Protos.TaskStatus failedStatus1 = TaskTestUtils.generateStatus(
                taskInfos.get(1).getTaskId(),
                Protos.TaskState.TASK_FAILED);

        stateStore.storeStatus(failedStatus0);
        stateStore.storeStatus(failedStatus1);

        DefaultRecoveryPlanManager recoveryPlanManager = new DefaultRecoveryPlanManager(
                stateStore,
                recoveryRequirementProvider,
                new UnconstrainedLaunchConstrainer(),
                new NeverFailureMonitor());

        List<Step> steps = recoveryPlanManager.createSteps(Collections.emptyList());
        Assert.assertEquals(1, steps.size());
    }
}
