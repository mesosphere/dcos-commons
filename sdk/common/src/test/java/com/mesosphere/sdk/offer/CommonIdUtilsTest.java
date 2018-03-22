package com.mesosphere.sdk.offer;

import java.util.UUID;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link CommonIdUtils}.
 */
public class CommonIdUtilsTest {
    private static final String TEST_SERVICE_NAME = "test-service_name";
    private static final String TEST_TASK_NAME = "test_task-name";
    private static final String TEST_OTHER_NAME = "test-other_name";

    // Task id

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(validTaskId));
        Assert.assertFalse(CommonIdUtils.toServiceName(validTaskId).isPresent());
    }

    @Test
    public void testValidToUnderscoreTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId("___id");
        Assert.assertEquals("_", CommonIdUtils.toTaskName(validTaskId));
        Assert.assertFalse(CommonIdUtils.toServiceName(validTaskId).isPresent());
    }

    @Test
    public void testMultiValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_OTHER_NAME + "__" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(validTaskId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toServiceName(validTaskId).get());
    }

    @Test
    public void testMultiValidToEmptyTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_OTHER_NAME + "____id");
        Assert.assertEquals("", CommonIdUtils.toTaskName(validTaskId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toServiceName(validTaskId).get());
    }

    @Test
    public void testMultiToUnderscoreTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_OTHER_NAME + "___id");
        Assert.assertEquals(TEST_OTHER_NAME + "_", CommonIdUtils.toTaskName(validTaskId));
        Assert.assertFalse(CommonIdUtils.toServiceName(validTaskId).isPresent());
    }

    @Test
    public void testToTaskId() throws Exception {
        Protos.TaskID taskId = CommonIdUtils.toTaskId(TEST_SERVICE_NAME, TEST_TASK_NAME);
        Assert.assertTrue(taskId.getValue().startsWith(TEST_SERVICE_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(taskId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(taskId));
        Assert.assertEquals(TEST_SERVICE_NAME, CommonIdUtils.toServiceName(taskId).get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidServiceToTaskId() throws Exception {
        // Disallow double underscores in service names, reserved for multipart:
        CommonIdUtils.toTaskId(TEST_OTHER_NAME + "__" + TEST_SERVICE_NAME, TEST_TASK_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidNameToTaskId() throws Exception {
        // Disallow double underscores in task names, reserved for multipart:
        CommonIdUtils.toTaskId(TEST_SERVICE_NAME, TEST_OTHER_NAME + "__" + TEST_TASK_NAME);
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonIdUtils.toTaskName(getTaskId(TEST_TASK_NAME + "_id"));
    }

    // Executor id

    @Test
    public void testValidToExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId(TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertFalse(CommonIdUtils.toServiceName(validExecutorId).isPresent());
    }

    @Test
    public void testValidToUnderscoreExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId("___id");
        Assert.assertEquals("_", CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertFalse(CommonIdUtils.toServiceName(validExecutorId).isPresent());
    }

    @Test
    public void testMultiValidToExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId(TEST_OTHER_NAME + "__" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toServiceName(validExecutorId).get());
    }

    @Test
    public void testMultiValidToEmptyExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId(TEST_OTHER_NAME + "____id");
        Assert.assertEquals("", CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toServiceName(validExecutorId).get());
    }

    @Test
    public void testMultiToUnderscoreExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId(TEST_OTHER_NAME + "___id");
        Assert.assertEquals(TEST_OTHER_NAME + "_", CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertFalse(CommonIdUtils.toServiceName(validExecutorId).isPresent());
    }

    @Test
    public void testToExecutorId() throws Exception {
        Protos.ExecutorID executorId = CommonIdUtils.toExecutorId(TEST_SERVICE_NAME, TEST_TASK_NAME);
        Assert.assertTrue(executorId.getValue().startsWith(TEST_SERVICE_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(executorId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(executorId));
        Assert.assertEquals(TEST_SERVICE_NAME, CommonIdUtils.toServiceName(executorId).get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidServiceToExecutorId() throws Exception {
        // Disallow double underscores in service names, reserved for multipart:
        CommonIdUtils.toExecutorId(TEST_OTHER_NAME + "__" + TEST_SERVICE_NAME, TEST_TASK_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidNameToExecutorId() throws Exception {
        // Disallow double underscores in task names, reserved for multipart:
        CommonIdUtils.toExecutorId(TEST_SERVICE_NAME, TEST_OTHER_NAME + "__" + TEST_TASK_NAME);
    }

    @Test(expected = TaskException.class)
    public void testInvalidToExecutorName() throws Exception {
        CommonIdUtils.toExecutorName(getExecutorId(TEST_TASK_NAME + "_id"));
    }

    private static Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }

    private static Protos.ExecutorID getExecutorId(String value) {
        return Protos.ExecutorID.newBuilder().setValue(value).build();
    }
}
