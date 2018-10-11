package com.mesosphere.sdk.offer;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStoreUtilsTest;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class tests the TaskUtils class.
 */
public class TaskUtilsTest {

    private static final String TEST_TASK_NAME = "test-task-name";

    private static final ConfigStore<ServiceSpec> TWO_ESSENTIAL_TWO_NONESSENTIAL = buildPodLayout(2, 2);
    private static final Collection<Protos.TaskInfo> TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS = Arrays.asList(
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 0, "essential0"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 0, "essential1"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 0, "nonessential0"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 0, "nonessential1"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 1, "essential0"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 1, "essential1"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 1, "nonessential0"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 1, "nonessential1"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 2, "essential0"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 2, "essential1"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 2, "nonessential0"),
            buildTask(TWO_ESSENTIAL_TWO_NONESSENTIAL, 2, "nonessential1"));

    private static final ConfigStore<ServiceSpec> TWO_ESSENTIAL = buildPodLayout(2, 0);
    private static final Collection<Protos.TaskInfo> TWO_ESSENTIAL_TASKS = Arrays.asList(
            buildTask(TWO_ESSENTIAL, 0, "essential0"),
            buildTask(TWO_ESSENTIAL, 0, "essential1"),
            buildTask(TWO_ESSENTIAL, 1, "essential0"),
            buildTask(TWO_ESSENTIAL, 1, "essential1"),
            buildTask(TWO_ESSENTIAL, 2, "essential0"),
            buildTask(TWO_ESSENTIAL, 2, "essential1"));

    private static final ConfigStore<ServiceSpec> TWO_NONESSENTIAL = buildPodLayout(0, 2);
    private static final Collection<Protos.TaskInfo> TWO_NONESSENTIAL_TASKS = Arrays.asList(
            buildTask(TWO_NONESSENTIAL, 0, "nonessential0"),
            buildTask(TWO_NONESSENTIAL, 0, "nonessential1"),
            buildTask(TWO_NONESSENTIAL, 1, "nonessential0"),
            buildTask(TWO_NONESSENTIAL, 1, "nonessential1"),
            buildTask(TWO_NONESSENTIAL, 2, "nonessential0"),
            buildTask(TWO_NONESSENTIAL, 2, "nonessential1"));

    private Persister persister;

    @Before
    public void beforeEach() throws Exception {
        this.persister = MemPersister.newBuilder().build();
    }

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = Protos.TaskID.newBuilder().setValue(TEST_TASK_NAME + "__id").build();
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonIdUtils.toTaskName(Protos.TaskID.newBuilder().setValue(TEST_TASK_NAME + "_id").build());
    }

    @Test
    public void testAreNotDifferentTaskSpecifications() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec();
        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsName() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                "new" + oldTaskSpecification.getName(),
                oldTaskSpecification.getCommand().get().getValue(),
                oldTaskSpecification.getResourceSet().getId(),
                TestPodFactory.CPU,
                TestPodFactory.MEM,
                TestPodFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsCmd() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                oldTaskSpecification.getName(),
                oldTaskSpecification.getCommand().get().getValue() + " && echo foo",
                oldTaskSpecification.getResourceSet().getId(),
                TestPodFactory.CPU,
                TestPodFactory.MEM,
                TestPodFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsResourcesLength() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .memory(6.)
                        .build());

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoResourceOverlap() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .memory(5.)
                        .build());

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreNotDifferentTaskSpecificationsResourcesMatch() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID)
                        .cpus(5.)
                        .memory(3.)
                        .build());
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                        .id(TestConstants.RESOURCE_SET_ID + "b")
                        .cpus(5.)
                        .memory(3.)
                        .build());

        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsConfigsLength() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec();
        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(DefaultConfigFileSpec.newBuilder()
                        .name("config")
                        .relativePath("../relative/path/to/config")
                        .templateContent("this is a config template")
                        .build()));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoConfigOverlap() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        DefaultConfigFileSpec.newBuilder()
                                .name("config")
                                .relativePath("../relative/path/to/config")
                                .templateContent("this is a config template")
                                .build(),
                        DefaultConfigFileSpec.newBuilder()
                                .name("config2")
                                .relativePath("../relative/path/to/config2")
                                .templateContent("second config")
                                .build()));

        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        DefaultConfigFileSpec.newBuilder()
                                .name("config")
                                .relativePath("../relative/path/to/config")
                                .templateContent("this is a diff config template")
                                .build(),
                        DefaultConfigFileSpec.newBuilder()
                                .name("config2")
                                .relativePath("../relative/path/to/config2")
                                .templateContent("diff second config")
                                .build()));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreNotDifferentTaskSpecificationsReorderedConfigMatch() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        DefaultConfigFileSpec.newBuilder()
                                .name("config")
                                .relativePath("../relative/path/to/config")
                                .templateContent("a config template")
                                .build(),
                        DefaultConfigFileSpec.newBuilder()
                                .name("config2")
                                .relativePath("../relative/path/to/config2")
                                .templateContent("second config")
                                .build()));

        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        DefaultConfigFileSpec.newBuilder()
                                .name("config2")
                                .relativePath("../relative/path/to/config2")
                                .templateContent("second config")
                                .build(),
                        DefaultConfigFileSpec.newBuilder()
                                .name("config")
                                .relativePath("../relative/path/to/config")
                                .templateContent("a config template")
                                .build()));

        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsVIP() {
        Protos.Value.Builder portValueBuilder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        portValueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(80)
                .setEnd(80);

        ResourceSet oldResourceSet = mock(ResourceSet.class);
        ResourceSet newResourceSet = mock(ResourceSet.class);

        NamedVIPSpec.Builder builder = NamedVIPSpec.newBuilder()
                .protocol("protocol")
                .vipName(TestConstants.VIP_NAME)
                .vipPort(TestConstants.VIP_PORT);
        builder
                .envKey("env-key")
                .portName("port-name")
                .visibility(TestConstants.PORT_VISIBILITY)
                .networkNames(Collections.singleton("network-name"));
        builder
                .value(portValueBuilder.build())
                .role(TestConstants.ROLE)
                .preReservedRole(TestConstants.PRE_RESERVED_ROLE)
                .principal(TestConstants.PRINCIPAL)
                .build();

        ResourceSpec oldVip = builder.build();

        ResourceSpec newVip = builder
                .vipName(TestConstants.VIP_NAME + "-different") // Different vip name
                .build();

        when(oldResourceSet.getId()).thenReturn(TestConstants.RESOURCE_SET_ID);
        when(oldResourceSet.getResources()).thenReturn(Arrays.asList(oldVip)); // Old VIP
        when(oldResourceSet.getVolumes()).thenReturn(Collections.emptyList());

        when(newResourceSet.getId()).thenReturn(TestConstants.RESOURCE_SET_ID);
        when(newResourceSet.getResources()).thenReturn(Arrays.asList(newVip)); // New VIP
        when(newResourceSet.getVolumes()).thenReturn(Collections.emptyList());

        TaskSpec oldTaskSpecification = DefaultTaskSpec.newBuilder(TestPodFactory.getTaskSpec())
                .resourceSet(oldResourceSet)
                .build();

        TaskSpec newTaskSpecification = DefaultTaskSpec.newBuilder(TestPodFactory.getTaskSpec())
                .resourceSet(newResourceSet)
                .build();

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConfigsSamePathFailsValidation() {
        TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        DefaultConfigFileSpec.newBuilder()
                                .name("config")
                                .relativePath("../relative/path/to/config")
                                .templateContent("this is a config template")
                                .build(),
                        DefaultConfigFileSpec.newBuilder()
                                .name("config2")
                                .relativePath("../relative/path/to/config")
                                .templateContent("same path should fail")
                                .build()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConfigsSameNameFailsValidation() {
        TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        DefaultConfigFileSpec.newBuilder()
                                .name("config")
                                .relativePath("../relative/path/to/config")
                                .templateContent("this is a config template")
                                .build(),
                        DefaultConfigFileSpec.newBuilder()
                                .name("config")
                                .relativePath("../relative/path/to/config2")
                                .templateContent("same name should fail")
                                .build()));
    }

    @Test
    public void testRelaunchFailedEssentialTaskInMixedPod() throws ConfigStoreException {
        // layout: 3 'server' pod instances, each with 2 essential + 2 nonessential tasks
        // failed: server-0-essential0, server-0-essential1, server-1-essential1
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(
                TWO_ESSENTIAL_TWO_NONESSENTIAL,
                TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                getTaskStatuses(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS),
                filterTasksByName(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                        "server-0-essential0", "server-0-essential1", "server-1-essential1"));

        Assert.assertEquals(2, reqs.size());
        PodInstanceRequirement req = reqs.get(0);
        Assert.assertEquals("server-0:[essential0, essential1, nonessential0, nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("essential0", "essential1", "nonessential0", "nonessential1"), req.getTasksToLaunch());
        req = reqs.get(1);
        Assert.assertEquals("server-1:[essential0, essential1, nonessential0, nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("essential0", "essential1", "nonessential0", "nonessential1"), req.getTasksToLaunch());
    }

    @Test
    public void testRelaunchFailedNonEssentialTaskInMixedPod() throws ConfigStoreException {
        // layout: 3 'server' pod instances, each with 2 essential + 2 nonessential tasks
        // failed: server-0-nonessential0, server-0-nonessential1, server-1-nonessential1
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(
                TWO_ESSENTIAL_TWO_NONESSENTIAL,
                TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                getTaskStatuses(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS),
                filterTasksByName(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                        "server-0-nonessential0", "server-0-nonessential1", "server-1-nonessential1"));

        Assert.assertEquals(2, reqs.size());
        PodInstanceRequirement req = reqs.get(0);
        Assert.assertEquals("server-0:[nonessential0, nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("nonessential0", "nonessential1"), req.getTasksToLaunch());
        req = reqs.get(1);
        Assert.assertEquals("server-1:[nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("nonessential1"), req.getTasksToLaunch());
    }

    @Test
    public void testRelaunchFailedMixedTasksInMixedPod() throws ConfigStoreException {
        // layout: 3 'server' pod instances, each with 2 essential + 2 nonessential tasks
        // failed: server-0-essential0, server-0-nonessential0, server-1-nonessential1
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(
                TWO_ESSENTIAL_TWO_NONESSENTIAL,
                TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                getTaskStatuses(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS),
                filterTasksByName(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                        "server-0-essential0", "server-0-nonessential0", "server-1-nonessential1"));

        Assert.assertEquals(2, reqs.size());
        PodInstanceRequirement req = reqs.get(0);
        Assert.assertEquals("server-0:[essential0, essential1, nonessential0, nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("essential0", "essential1", "nonessential0", "nonessential1"), req.getTasksToLaunch());
        req = reqs.get(1);
        Assert.assertEquals("server-1:[nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("nonessential1"), req.getTasksToLaunch());
    }

    @Test
    public void testRelaunchFailedEssentialTasksInEssentialPod() throws ConfigStoreException {
        // layout: 3 'server' pod instances, each with 2 essential tasks (only)
        // failed: server-0-essential0, server-0-essential1, server-1-essential1
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(
                TWO_ESSENTIAL,
                TWO_ESSENTIAL_TASKS,
                getTaskStatuses(TWO_ESSENTIAL_TASKS),
                filterTasksByName(TWO_ESSENTIAL_TASKS,
                        "server-0-essential0", "server-0-essential1", "server-1-essential1"));

        Assert.assertEquals(reqs.toString(), 2, reqs.size());
        PodInstanceRequirement req = reqs.get(0);
        Assert.assertEquals("server-0:[essential0, essential1]", req.getName());
        Assert.assertEquals(Arrays.asList("essential0", "essential1"), req.getTasksToLaunch());
        req = reqs.get(1);
        Assert.assertEquals("server-1:[essential0, essential1]", req.getName());
        Assert.assertEquals(Arrays.asList("essential0", "essential1"), req.getTasksToLaunch());
    }

    @Test
    public void testRelaunchFailedNonessentialTasksInNonessentialPod() throws ConfigStoreException {
        // layout: 3 'server' pod instances, each with 2 nonessential tasks (only)
        // failed: server-0-nonessential0, server-0-nonessential1, server-1-nonessential1
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(
                TWO_NONESSENTIAL,
                TWO_NONESSENTIAL_TASKS,
                getTaskStatuses(TWO_NONESSENTIAL_TASKS),
                filterTasksByName(TWO_NONESSENTIAL_TASKS,
                        "server-0-nonessential0", "server-0-nonessential1", "server-1-nonessential1"));

        Assert.assertEquals(reqs.toString(), 2, reqs.size());
        PodInstanceRequirement req = reqs.get(0);
        Assert.assertEquals("server-0:[nonessential0, nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("nonessential0", "nonessential1"), req.getTasksToLaunch());
        req = reqs.get(1);
        Assert.assertEquals("server-1:[nonessential1]", req.getName());
        Assert.assertEquals(Arrays.asList("nonessential1"), req.getTasksToLaunch());
    }

    @Test
    public void testTaskLostNeedsRecovery() {
        Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                .setState(Protos.TaskState.TASK_LOST);
        taskStatusBuilder.getTaskIdBuilder().setValue(UUID.randomUUID().toString());
        Assert.assertTrue(TaskUtils.isRecoveryNeeded(taskStatusBuilder.build()));
    }

    @Test
    public void testEmptyTasksHasNoTasksNeedingRecovery() throws TaskException {
        assertThat(TaskUtils.getTasksNeedingRecovery(null, Collections.emptyList(), Collections.emptyList()), is(empty()));
    }

    @Test
    public void testTaskWithNoStatusDoesNotNeedRecovery() throws TaskException {
        Protos.TaskInfo taskInfo = newTaskInfo("hey");

        assertThat(TaskUtils.getTasksNeedingRecovery(null, Collections.singleton(taskInfo), Collections.emptyList()), is(empty()));
    }

    @Test
    public void testRunningTaskDoesNotNeedRecoveryIfRunning() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-node", configStore);
        Protos.TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, Protos.TaskState.TASK_RUNNING);

        assertThat(TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.singleton(taskStatus)),
                is(empty()));
    }

    @Test
    public void testRunningTaskNeedsRecoveryIfFailed() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-node", configStore);
        Protos.TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, Protos.TaskState.TASK_FAILED);

        assertThat(TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.singleton(taskStatus)),
                is(ImmutableList.of(taskInfo)));
    }

    @Test(expected = TaskException.class)
    public void testNonPresentTaskRaisesError() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-not-present", configStore);
        Protos.TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, Protos.TaskState.TASK_RUNNING);

        TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.singleton(taskStatus));
    }

    @Test
    public void testTaskInfoWithNoStatusRequiresNoRecovery() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-not-present", configStore);

        assertThat(TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.emptyList()),
                is(empty()));
    }

    @Test
    public void testFinishedTaskDoesNotNeedRecoveryIfFailed() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-format", configStore);
        Protos.TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, Protos.TaskState.TASK_FAILED);

        assertThat(TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.singleton(taskStatus)),
                is(empty()));
    }

    @Test
    public void testFinishedTaskDoesNotNeedRecoveryIfFinished() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-format", configStore);
        Protos.TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, Protos.TaskState.TASK_FINISHED);

        assertThat(TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.singleton(taskStatus)),
                is(empty()));
    }

    @Test
    public void testFinishedTaskDoesNotNeedRecoveryIfRunning() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-format", configStore);
        Protos.TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, Protos.TaskState.TASK_RUNNING);

        assertThat(TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.singleton(taskStatus)),
                is(empty()));
    }

    @Test
    public void testPermanentlyFailedTaskNeedsRecovery() throws Exception {
        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        Protos.TaskInfo taskInfo = newTaskInfo("name-0-node", configStore);

        // Set status as RUNNING
        Protos.TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, Protos.TaskState.TASK_RUNNING);

        // Mark task as permanently failed
        taskInfo = taskInfo.toBuilder()
                .setLabels(new TaskLabelWriter(taskInfo).setPermanentlyFailed().toProto())
                .build();

        // Even though the TaskStatus is RUNNING, it can now be recovered since it has been marked as
        // permanently failed.
        assertThat(TaskUtils.getTasksNeedingRecovery(configStore, Collections.singleton(taskInfo), Collections.singleton(taskStatus)),
                is(ImmutableList.of(taskInfo)));
    }

    private static Protos.TaskInfo newTaskInfo(
            final String taskName, final ConfigStore<ServiceSpec> configStore) throws ConfigStoreException {
        Protos.TaskInfo.Builder taskInfoBuilder = newTaskInfo(taskName).toBuilder();

        // POD type
        final UUID targetConfig = configStore.getTargetConfig();
        final int podIndex = 0;
        final String podType = configStore.fetch(targetConfig).getPods().get(podIndex).getType();

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(targetConfig)
                .setType(podType)
                .setIndex(podIndex)
                .toProto());

        return taskInfoBuilder.build();
    }

    private static Protos.TaskInfo newTaskInfo(final String taskName) {
        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, taskName));
        taskBuilder.getSlaveIdBuilder().setValue("proto-field-required");
        return taskBuilder.build();
    }

    private ConfigStore<ServiceSpec> newConfigStore(final Persister persister) throws Exception {
        ServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(
                new File(TaskUtilsTest.class.getClassLoader().getResource("resource-set-seq.yml").getFile()),
                SchedulerConfigTestUtils.getTestSchedulerConfig())
                .build();

        ConfigStore<ServiceSpec> configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);
        // At startup, the the service spec must be stored, and the target config must be set to the stored spec.
        configStore.setTargetConfig(configStore.store(serviceSpec));
        return configStore;
    }

    private static Collection<Protos.TaskStatus> getTaskStatuses(Collection<Protos.TaskInfo> taskInfos) {
        return taskInfos.stream().map(task -> Protos.TaskStatus.newBuilder()
                    .setState(Protos.TaskState.TASK_STAGING)
                    .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                    .build())
                .collect(Collectors.toList());
    }

    private static ConfigStore<ServiceSpec> buildPodLayout(int essentialTasks, int nonessentialTasks) {
        DefaultPodSpec.Builder podBuilder =
                DefaultPodSpec.newBuilder(
                        "server",
                        3,
                        Collections.emptyList()) // Tasks added below
                        .type("server")
                        .count(3);
        for (int i = 0; i < essentialTasks; ++i) {
            podBuilder.addTask(buildTaskTemplate(String.format("essential%d", i))
                    .goalState(GoalState.RUNNING)
                    .build());
        }
        for (int i = 0; i < nonessentialTasks; ++i) {
            podBuilder.addTask(buildTaskTemplate(String.format("nonessential%d", i))
                    .goalState(GoalState.RUNNING)
                    .essential(false)
                    .build());
        }
        // should be ignored for recovery purposes:
        podBuilder.addTask(buildTaskTemplate("once")
                .goalState(GoalState.ONCE)
                .build());

        ServiceSpec serviceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .addPod(podBuilder.build())
                .build();
        ConfigStore<ServiceSpec> configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), MemPersister.newBuilder().build());
        try {
            configStore.setTargetConfig(configStore.store(serviceSpec));
        } catch (ConfigStoreException e) {
            throw new IllegalStateException(e);
        }
        return configStore;
    }

    private static DefaultTaskSpec.Builder buildTaskTemplate(String taskName) {
        return DefaultTaskSpec.newBuilder()
                .commandSpec(DefaultCommandSpec.newBuilder(Collections.emptyMap())
                        .value("echo this is " + taskName)
                        .build())
                .resourceSet(DefaultResourceSet
                        .newBuilder(taskName + "-role", Constants.ANY_ROLE, taskName + "-principal")
                        .id(taskName + "-resources")
                        .cpus(0.1)
                        .build())
                .name(taskName);
    }

    private static Protos.TaskInfo buildTask(ConfigStore<ServiceSpec> configStore, int index, String task) {
        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setName(String.format("server-%d-%s", index, task));
        UUID id;
        try {
            id = configStore.getTargetConfig();
        } catch (ConfigStoreException e) {
            throw new IllegalStateException(e);
        }
        taskBuilder.setLabels(new TaskLabelWriter(taskBuilder)
                .setIndex(index)
                .setType("server")
                .setTargetConfiguration(id)
                .toProto());
        return taskBuilder.build();
    }

    private static Collection<Protos.TaskInfo> filterTasksByName(Collection<Protos.TaskInfo> tasks, String... names) {
        Set<String> namesSet = new HashSet<>(Arrays.asList(names));
        return tasks.stream()
                .filter(t -> namesSet.contains(t.getName()))
                .collect(Collectors.toList());
    }
}
