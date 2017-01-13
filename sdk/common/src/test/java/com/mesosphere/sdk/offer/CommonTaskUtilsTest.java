package com.mesosphere.sdk.offer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.DefaultConfigFileSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.commons.io.FileUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

/**
 * This class tests the CommonTaskUtils class.
 */
public class CommonTaskUtilsTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private static final String testTaskName = "test-task-name";
    private static final String testTaskId = "test-task-id";
    private static final String testAgentId = "test-agent-id";
    private static final UUID testTargetConfigurationId = UUID.randomUUID();

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(testTaskName + "__id");
        Assert.assertEquals(testTaskName, CommonTaskUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonTaskUtils.toTaskName(getTaskId(testTaskName + "_id"));
    }

    @Test(expected = TaskException.class)
    public void testGetTargetConfigurationFailure() throws Exception {
        CommonTaskUtils.getTargetConfiguration(getTestTaskInfo());
    }

    @Test
    public void testSetTargetConfiguration() throws Exception {
        Protos.TaskInfo taskInfo = CommonTaskUtils.setTargetConfiguration(
                getTestTaskInfo().toBuilder(), testTargetConfigurationId).build();
        Assert.assertEquals(testTargetConfigurationId, CommonTaskUtils.getTargetConfiguration(taskInfo));
    }

    @Test
    public void testSetGetConfigTemplates() throws InvalidProtocolBufferException {
        Protos.TaskInfo.Builder taskBuilder = getTestTaskInfo().toBuilder();
        Collection<ConfigFileSpec> configs = Arrays.asList(
                new DefaultConfigFileSpec("../relative/path/to/config", "this is a config template"),
                new DefaultConfigFileSpec("../relative/path/to/config2", "this is a second config template"));
        CommonTaskUtils.setConfigFiles(taskBuilder, configs);
        Assert.assertEquals(configs, CommonTaskUtils.getConfigFiles(taskBuilder.build()));
    }

    @Test(expected = IllegalStateException.class)
    public void testSetTemplatesTooBig() throws InvalidProtocolBufferException {
        Protos.TaskInfo.Builder taskBuilder = getTestTaskInfo().toBuilder();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256 * 1024; ++i) {
            sb.append('a');
        }
        Collection<ConfigFileSpec> configs = Arrays.asList(
                new DefaultConfigFileSpec("../relative/path/to/config", sb.toString()),
                new DefaultConfigFileSpec("../relative/path/to/config2", sb.toString()),
                new DefaultConfigFileSpec("../relative/path/to/config3", "a"));
        CommonTaskUtils.setConfigFiles(taskBuilder, configs);
    }

    @Test(expected = TaskException.class)
    public void testGetMissingTaskTypeFails() throws TaskException {
        CommonTaskUtils.getType(getTestTaskInfo());
    }

    @Test
    public void testSetGetTaskType() throws TaskException {
        Assert.assertEquals("foo", CommonTaskUtils.getType(CommonTaskUtils.setType(
                getTestTaskInfo().toBuilder(), "foo").build()));
        Assert.assertEquals("", CommonTaskUtils.getType(CommonTaskUtils.setType(
                getTestTaskInfo().toBuilder(), "").build()));

    }

    @Test
    public void testApplyEnvToMustache() throws IOException {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        String yaml = FileUtils.readFileToString(file);
        Assert.assertTrue(yaml.contains("api-port: {{PORT0}}"));
        String renderedYaml = CommonTaskUtils.applyEnvToMustache(yaml, System.getenv());
        Assert.assertTrue(renderedYaml.contains("api-port: 8080"));
    }

    @Test
    public void testReadinessCheckTagging() throws TaskException {
        Protos.TaskInfo.Builder builder = Protos.TaskInfo.newBuilder()
                .setName(TestConstants.TASK_NAME)
                .setTaskId(TestConstants.TASK_ID)
                .setSlaveId(TestConstants.AGENT_ID);
        Protos.HealthCheck inReadinessCheck = Protos.HealthCheck.newBuilder()
                .setDelaySeconds(1.0)
                .build();
        CommonTaskUtils.setReadinessCheck(builder, inReadinessCheck);
        Protos.HealthCheck outReadinessCheck = CommonTaskUtils.getReadinessCheck(builder.build()).get();

        Assert.assertEquals(inReadinessCheck.getDelaySeconds(), outReadinessCheck.getDelaySeconds(), 0.0);
    }

    private static Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }

    private static Protos.TaskInfo getTestTaskInfo() {
        return Protos.TaskInfo.newBuilder()
                .setName(testTaskName)
                .setTaskId(Protos.TaskID.newBuilder().setValue(testTaskId))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(testAgentId))
                .build();
    }
}
