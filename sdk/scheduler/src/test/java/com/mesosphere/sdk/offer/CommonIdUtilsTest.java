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
    private static final String TEST_FOLDERED_SERVICE_NAME = "/path/to/test-service_name";
    private static final String TEST_FOLDERED_SERVICE_NAME2 = "path/to.test-service_name";
    private static final String TEST_FOLDERED_SANITIZED_NAME = "path.to.test-service_name";
    private static final String TEST_TASK_NAME = "test_task-name";
    private static final String TEST_OTHER_NAME = "test-other_name";

    @Test
    public void testSanitizedNames() {
        Assert.assertEquals("path.to.service", CommonIdUtils.toSanitizedServiceName("/path/to/service"));
        Assert.assertEquals("path.to.service", CommonIdUtils.toSanitizedServiceName("//path/to/service///"));
        Assert.assertEquals("path.to.service", CommonIdUtils.toSanitizedServiceName("path/to/service"));
        Assert.assertEquals("service", CommonIdUtils.toSanitizedServiceName("/service"));
        Assert.assertEquals("service", CommonIdUtils.toSanitizedServiceName("///service//"));
    }

    // Task id

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(validTaskId));
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validTaskId).isPresent());
    }

    @Test
    public void testValidToUnderscoreTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId("___id");
        Assert.assertEquals("_", CommonIdUtils.toTaskName(validTaskId));
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validTaskId).isPresent());
    }

    @Test
    public void testMultiValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_OTHER_NAME + "__" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(validTaskId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toSanitizedServiceName(validTaskId).get());

        validTaskId = getTaskId(TEST_OTHER_NAME + "___" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(validTaskId));
        Assert.assertEquals(TEST_OTHER_NAME + "_", CommonIdUtils.toSanitizedServiceName(validTaskId).get());

        validTaskId = getTaskId("_" + TEST_OTHER_NAME + "___" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(validTaskId));
        Assert.assertEquals("_" + TEST_OTHER_NAME + "_", CommonIdUtils.toSanitizedServiceName(validTaskId).get());
    }

    @Test
    public void testMultiValidToEmptyTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_OTHER_NAME + "____id");
        Assert.assertEquals("", CommonIdUtils.toTaskName(validTaskId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toSanitizedServiceName(validTaskId).get());
    }

    @Test
    public void testMultiToUnderscoreTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(TEST_OTHER_NAME + "___id");
        Assert.assertEquals(TEST_OTHER_NAME + "_", CommonIdUtils.toTaskName(validTaskId));
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validTaskId).isPresent());

        validTaskId = getTaskId("_" + TEST_OTHER_NAME + "___id");
        Assert.assertEquals("_" + TEST_OTHER_NAME + "_", CommonIdUtils.toTaskName(validTaskId));
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validTaskId).isPresent());
    }

    @Test
    public void testToTaskId() throws Exception {
        Protos.TaskID taskId = CommonIdUtils.toTaskId(TEST_SERVICE_NAME, TEST_TASK_NAME);
        Assert.assertTrue(taskId.getValue().startsWith(TEST_SERVICE_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(taskId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(taskId));
        Assert.assertEquals(TEST_SERVICE_NAME, CommonIdUtils.toSanitizedServiceName(taskId).get());
    }

    @Test
    public void testFoldered1ToTaskId() throws Exception {
        Protos.TaskID taskId = CommonIdUtils.toTaskId(TEST_FOLDERED_SERVICE_NAME, TEST_TASK_NAME);
        Assert.assertTrue(taskId.getValue().startsWith(TEST_FOLDERED_SANITIZED_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(taskId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(taskId));
        Assert.assertEquals(TEST_FOLDERED_SANITIZED_NAME, CommonIdUtils.toSanitizedServiceName(taskId).get());
    }

    @Test
    public void testFoldered2ToTaskId() throws Exception {
        Protos.TaskID taskId = CommonIdUtils.toTaskId(TEST_FOLDERED_SERVICE_NAME2, TEST_TASK_NAME);
        Assert.assertTrue(taskId.getValue().startsWith(TEST_FOLDERED_SANITIZED_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(taskId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(taskId));
        Assert.assertEquals(TEST_FOLDERED_SANITIZED_NAME, CommonIdUtils.toSanitizedServiceName(taskId).get());
    }

    @Test
    public void testExtractTaskFromExtraElements() throws Exception {
        // Just in case, we support additional elements at the start of the id for future use.
        Protos.TaskID taskId = Protos.TaskID.newBuilder()
                .setValue("something-else__" + TEST_FOLDERED_SERVICE_NAME2 + "__" + TEST_TASK_NAME + "__uuid")
                .build();
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toTaskName(taskId));
        Assert.assertEquals(TEST_FOLDERED_SERVICE_NAME2, CommonIdUtils.toSanitizedServiceName(taskId).get());
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
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validExecutorId).isPresent());
    }

    @Test
    public void testValidToUnderscoreExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId("___id");
        Assert.assertEquals("_", CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validExecutorId).isPresent());
    }

    @Test
    public void testMultiValidToExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId(TEST_OTHER_NAME + "__" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toSanitizedServiceName(validExecutorId).get());

        validExecutorId = getExecutorId(TEST_OTHER_NAME + "___" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertEquals(TEST_OTHER_NAME + "_", CommonIdUtils.toSanitizedServiceName(validExecutorId).get());

        validExecutorId = getExecutorId("_" + TEST_OTHER_NAME + "___" + TEST_TASK_NAME + "__id");
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertEquals("_" + TEST_OTHER_NAME + "_", CommonIdUtils.toSanitizedServiceName(validExecutorId).get());
    }

    @Test
    public void testMultiValidToEmptyExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId(TEST_OTHER_NAME + "____id");
        Assert.assertEquals("", CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertEquals(TEST_OTHER_NAME, CommonIdUtils.toSanitizedServiceName(validExecutorId).get());
    }

    @Test
    public void testMultiToUnderscoreExecutorName() throws Exception {
        Protos.ExecutorID validExecutorId = getExecutorId(TEST_OTHER_NAME + "___id");
        Assert.assertEquals(TEST_OTHER_NAME + "_", CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validExecutorId).isPresent());

        validExecutorId = getExecutorId("_" + TEST_OTHER_NAME + "___id");
        Assert.assertEquals("_" + TEST_OTHER_NAME + "_", CommonIdUtils.toExecutorName(validExecutorId));
        Assert.assertFalse(CommonIdUtils.toSanitizedServiceName(validExecutorId).isPresent());
    }

    @Test
    public void testToExecutorId() throws Exception {
        Protos.ExecutorID executorId = CommonIdUtils.toExecutorId(TEST_SERVICE_NAME, TEST_TASK_NAME);
        Assert.assertTrue(executorId.getValue().startsWith(TEST_SERVICE_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(executorId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(executorId));
        Assert.assertEquals(TEST_SERVICE_NAME, CommonIdUtils.toSanitizedServiceName(executorId).get());
    }

    @Test
    public void testFoldered1ToExecutorId() throws Exception {
        Protos.ExecutorID executorId = CommonIdUtils.toExecutorId(TEST_FOLDERED_SERVICE_NAME, TEST_TASK_NAME);
        Assert.assertTrue(executorId.getValue().startsWith(TEST_FOLDERED_SANITIZED_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(executorId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(executorId));
        Assert.assertEquals(TEST_FOLDERED_SANITIZED_NAME, CommonIdUtils.toSanitizedServiceName(executorId).get());
    }

    @Test
    public void testFoldered2ToExecutorId() throws Exception {
        Protos.ExecutorID executorId = CommonIdUtils.toExecutorId(TEST_FOLDERED_SERVICE_NAME2, TEST_TASK_NAME);
        Assert.assertTrue(executorId.getValue().startsWith(TEST_FOLDERED_SANITIZED_NAME + "__" + TEST_TASK_NAME + "__"));
        Assert.assertNotNull(UUID.fromString(executorId.getValue().split("__")[2]));
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(executorId));
        Assert.assertEquals(TEST_FOLDERED_SANITIZED_NAME, CommonIdUtils.toSanitizedServiceName(executorId).get());
    }

    @Test
    public void testExtractExecutorFromExtraElements() throws Exception {
        // Just in case, we support additional elements at the start of the id for future use.
        Protos.ExecutorID executorId = Protos.ExecutorID.newBuilder()
                .setValue("something-else__" + TEST_FOLDERED_SERVICE_NAME2 + "__" + TEST_TASK_NAME + "__uuid")
                .build();
        Assert.assertEquals(TEST_TASK_NAME, CommonIdUtils.toExecutorName(executorId));
        Assert.assertEquals(TEST_FOLDERED_SERVICE_NAME2, CommonIdUtils.toSanitizedServiceName(executorId).get());
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
