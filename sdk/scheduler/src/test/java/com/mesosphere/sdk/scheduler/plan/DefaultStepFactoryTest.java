package com.mesosphere.sdk.scheduler.plan;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;


/**
 * This class tests the {@link DefaultStepFactory} class.
 */
public class DefaultStepFactoryTest {

    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    private StepFactory stepFactory;
    private ConfigStore<ServiceSpec> configStore;
    private StateStore stateStore;

    @Test
    public void testGetStepFailsOnMultipleResourceSetReferences() throws Exception {

        PodInstance podInstance = getPodInstanceWithSameResourceSets();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());
        Step step = stepFactory.getStep(podInstance, tasksToLaunch);
        Assert.assertEquals(Status.ERROR, step.getStatus());
    }

    @Test
    public void testGetStepFailsOnDuplicateDNSNames() throws Exception {

        PodInstance podInstance = getPodInstanceWithSameDnsPrefixes();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());
        Step step = stepFactory.getStep(podInstance, tasksToLaunch);
        Assert.assertEquals(Status.ERROR, step.getStatus());
    }

    @Test
    public void testInitialStateForRunningTaskOnDefaultExecutorDependsOnReadinessCheck() throws Exception {

        Capabilities mockCapabilities = Mockito.mock(Capabilities.class);
        Mockito.when(mockCapabilities.supportsDefaultExecutor()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        PodInstance podInstance = getPodInstanceWithASingleTask();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        UUID configId = UUID.randomUUID();

        configStore.setTargetConfig(configId);

        String taskName = podInstance.getName() + '-' + tasksToLaunch.get(0);
        stateStore.storeTasks(ImmutableList.of(
                Protos.TaskInfo.newBuilder()
                        .setName(taskName)
                        .setTaskId(CommonIdUtils.toTaskId(taskName))
                        .setSlaveId(Protos.SlaveID.newBuilder()
                                .setValue("proto-field-required")
                        )
                        .setLabels(new TaskLabelWriter(TestConstants.TASK_INFO)
                                .setTargetConfiguration(configId)
                                .setReadinessCheck(Protos.HealthCheck.newBuilder().build())
                                .toProto())
                        .build()));


        Protos.TaskInfo taskInfo = stateStore.fetchTask(taskName).get();
        stateStore.storeStatus(taskName,
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .setTaskId(taskInfo.getTaskId())
                        .setLabels(Protos.Labels.newBuilder().addLabels(Protos.Label.newBuilder().setKey("readiness_check_passed").setValue("false").build()).build())
                        .build());


        assertThat(((DefaultStepFactory) stepFactory).hasReachedGoalState(podInstance, stateStore.fetchTask(taskName).get()), is(false));

        Step step = stepFactory.getStep(podInstance, tasksToLaunch);

        assertThat(step.isComplete(), is(false));
        assertThat(step.isPending(), is(true));


        stateStore.storeStatus(taskName,
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .setTaskId(taskInfo.getTaskId())
                        .setLabels(Protos.Labels.newBuilder().addLabels(Protos.Label.newBuilder().setKey("readiness_check_passed").setValue("true").build()).build())
                        .build());


        assertThat(((DefaultStepFactory) stepFactory).hasReachedGoalState(podInstance, stateStore.fetchTask(taskName).get()), is(true));

        step = stepFactory.getStep(podInstance, tasksToLaunch);

        assertThat(step.isComplete(), is(true));
        assertThat(step.isPending(), is(false));

    }

    @Test
    public void testTaskWithFinishedGoalStateCanReachGoalState() throws Exception {
        Capabilities mockCapabilities = Mockito.mock(Capabilities.class);
        Mockito.when(mockCapabilities.supportsDefaultExecutor()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        PodInstance podInstance = getPodInstanceWithFinishedTask();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        UUID configId = UUID.randomUUID();

        configStore.setTargetConfig(configId);

        String taskName = podInstance.getName() + '-' + tasksToLaunch.get(0);
        stateStore.storeTasks(ImmutableList.of(
                Protos.TaskInfo.newBuilder()
                        .setName(taskName)
                        .setTaskId(CommonIdUtils.toTaskId(taskName))
                        .setSlaveId(Protos.SlaveID.newBuilder()
                                .setValue("proto-field-required")
                        )
                        .setLabels(new TaskLabelWriter(TestConstants.TASK_INFO)
                                .setTargetConfiguration(configId)
                                .toProto())
                        .build()));
        Protos.TaskInfo taskInfo = stateStore.fetchTask(taskName).get();

        stateStore.storeStatus(taskName,
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .setTaskId(taskInfo.getTaskId())
                        .build());

        assertThat(((DefaultStepFactory) stepFactory).hasReachedGoalState(podInstance, stateStore.fetchTask(taskName).get()), is(false));

        stateStore.storeStatus(taskName,
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_FINISHED)
                        .setTaskId(taskInfo.getTaskId())
                        .build());


        assertThat(((DefaultStepFactory) stepFactory).hasReachedGoalState(podInstance, stateStore.fetchTask(taskName).get()), is(true));
    }


    @Test
    public void testInitialStateForRunningTaskOnCustomExecutorIsRunning() throws Exception {

        Capabilities mockCapabilities = Mockito.mock(Capabilities.class);
        Mockito.when(mockCapabilities.supportsDefaultExecutor()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);

        PodInstance podInstance = getPodInstanceWithASingleTask();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        UUID configId = UUID.randomUUID();

        configStore.setTargetConfig(configId);

        String taskName = podInstance.getName() + '-' + tasksToLaunch.get(0);
        stateStore.storeTasks(ImmutableList.of(
                Protos.TaskInfo.newBuilder()
                        .setName(taskName)
                        .setTaskId(CommonIdUtils.toTaskId(taskName))
                        .setSlaveId(Protos.SlaveID.newBuilder()
                                .setValue("proto-field-required")
                        )
                        .setLabels(new TaskLabelWriter(TestConstants.TASK_INFO)
                                .setTargetConfiguration(configId)
                                .setReadinessCheck(Protos.HealthCheck.newBuilder().build())
                                .toProto())
                        .build()));


        Protos.TaskInfo taskInfo = stateStore.fetchTask(taskName).get();
        stateStore.storeStatus(taskName,
                Protos.TaskStatus.newBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .setTaskId(taskInfo.getTaskId())
                        .setLabels(Protos.Labels.newBuilder().addLabels(Protos.Label.newBuilder().setKey("readiness_check_passed").setValue("false").build()).build())
                        .build());


        assertThat(((DefaultStepFactory) stepFactory).hasReachedGoalState(podInstance, stateStore.fetchTask(taskName).get()), is(true));

        final Step step = stepFactory.getStep(podInstance, tasksToLaunch);

        assertThat(step.isComplete(), is(true));
        assertThat(step.isPending(), is(false));

    }


    private PodInstance getPodInstanceWithASingleTask() throws Exception {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID);
        PodSpec podSpec = DefaultPodSpec.newBuilder(SCHEDULER_CONFIG.getExecutorURI())
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        Persister persister = new MemPersister();
        stateStore = new StateStore(persister);
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }


    private PodInstance getPodInstanceWithSameResourceSets() throws Exception {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID);
        PodSpec podSpec = DefaultPodSpec.newBuilder(SCHEDULER_CONFIG.getExecutorURI())
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        Persister persister = new MemPersister();
        stateStore = new StateStore(persister);
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }

    private PodInstance getPodInstanceWithFinishedTask() throws Exception {
        TaskSpec taskSpec =
                TestPodFactory.getFinishedTaskSpec(TestConstants.TASK_NAME, TestConstants.RESOURCE_SET_ID);
        PodSpec podSpec = DefaultPodSpec.newBuilder(SCHEDULER_CONFIG.getExecutorURI())
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        Persister persister = new MemPersister();
        stateStore = new StateStore(persister);
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }

    private PodInstance getPodInstanceWithSameDnsPrefixes() throws Exception {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(
                        TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(
                        TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX);
        PodSpec podSpec = DefaultPodSpec.newBuilder(SCHEDULER_CONFIG.getExecutorURI())
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        Persister persister = new MemPersister();
        stateStore = new StateStore(persister);
        configStore = new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }
}
