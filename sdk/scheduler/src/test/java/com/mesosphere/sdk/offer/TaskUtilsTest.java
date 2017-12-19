package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import javax.validation.ValidationException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class tests the TaskUtils class.
 */
public class TaskUtilsTest {
    private static final String testTaskName = "test-task-name";

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

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = Protos.TaskID.newBuilder().setValue(testTaskName + "__id").build();
        Assert.assertEquals(testTaskName, CommonIdUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonIdUtils.toTaskName(Protos.TaskID.newBuilder().setValue(testTaskName + "_id").build());
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
                Arrays.asList(new DefaultConfigFileSpec(
                        "config", "../relative/path/to/config", "this is a config template")));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoConfigOverlap() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "this is a config template"),
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config2", "second config")));

        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../diff/path/to/config", "this is a diff config template"),
                        new DefaultConfigFileSpec("config2", "../diff/path/to/config2", "diff second config")));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreNotDifferentTaskSpecificationsReorderedConfigMatch() {
        TaskSpec oldTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "a config template"),
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config2", "second config")));

        TaskSpec newTaskSpecification = TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config2", "second config"),
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "a config template")));

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

        ResourceSpec oldVip = new NamedVIPSpec(
                portValueBuilder.build(),
                TestConstants.ROLE,
                TestConstants.PRE_RESERVED_ROLE,
                TestConstants.PRINCIPAL,
                "env-key",
                "port-name",
                "protocol",
                TestConstants.PORT_VISIBILITY,
                TestConstants.VIP_NAME,
                TestConstants.VIP_PORT,
                Arrays.asList("network-name"));

        ResourceSpec newVip = new NamedVIPSpec(
                portValueBuilder.build(),
                TestConstants.ROLE,
                TestConstants.PRE_RESERVED_ROLE,
                TestConstants.PRINCIPAL,
                "env-key",
                "port-name",
                "protocol",
                TestConstants.PORT_VISIBILITY,
                TestConstants.VIP_NAME + "-different", // Different vip name
                TestConstants.VIP_PORT,
                Arrays.asList("network-name"));

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

    @Test(expected=ValidationException.class)
    public void testConfigsSamePathFailsValidation() {
        TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "this is a config template"),
                        new DefaultConfigFileSpec("config2", "../relative/path/to/config", "same path should fail")));
    }

    @Test(expected=ValidationException.class)
    public void testConfigsSameNameFailsValidation() {
        TestPodFactory.getTaskSpec(
                TestConstants.TASK_NAME,
                TestPodFactory.CMD.getValue(),
                TestPodFactory.getResourceSet(TestConstants.RESOURCE_SET_ID, 1, 2, 3),
                Arrays.asList(
                        new DefaultConfigFileSpec("config", "../relative/path/to/config", "this is a config template"),
                        new DefaultConfigFileSpec("config", "../relative/path/to/config2", "same name should fail")));
    }

    @Test
    public void testRelaunchFailedEssentialTaskInMixedPod() throws ConfigStoreException {
        // layout: 3 'server' pod instances, each with 2 essential + 2 nonessential tasks
        // failed: server-0-essential0, server-0-essential1, server-1-essential1
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(TWO_ESSENTIAL_TWO_NONESSENTIAL,
                filterTasksByName(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                        "server-0-essential0", "server-0-essential1", "server-1-essential1"),
                TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS);

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
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(TWO_ESSENTIAL_TWO_NONESSENTIAL,
                filterTasksByName(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                        "server-0-nonessential0", "server-0-nonessential1", "server-1-nonessential1"),
                TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS);

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
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(TWO_ESSENTIAL_TWO_NONESSENTIAL,
                filterTasksByName(TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS,
                        "server-0-essential0", "server-0-nonessential0", "server-1-nonessential1"),
                TWO_ESSENTIAL_TWO_NONESSENTIAL_TASKS);

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
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(TWO_ESSENTIAL,
                filterTasksByName(TWO_ESSENTIAL_TASKS,
                        "server-0-essential0", "server-0-essential1", "server-1-essential1"),
                TWO_ESSENTIAL_TASKS);

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
        List<PodInstanceRequirement> reqs = TaskUtils.getPodRequirements(TWO_NONESSENTIAL,
                filterTasksByName(TWO_NONESSENTIAL_TASKS,
                        "server-0-nonessential0", "server-0-nonessential1", "server-1-nonessential1"),
                TWO_NONESSENTIAL_TASKS);

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

    private static ConfigStore<ServiceSpec> buildPodLayout(int essentialTasks, int nonessentialTasks) {
        DefaultPodSpec.Builder podBuilder = DefaultPodSpec.newBuilder("executor-uri")
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
        ConfigStore<ServiceSpec> configStore;
        try {
            configStore = new ConfigStore<>(
                    DefaultServiceSpec.getConfigurationFactory(serviceSpec), new MemPersister());
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
