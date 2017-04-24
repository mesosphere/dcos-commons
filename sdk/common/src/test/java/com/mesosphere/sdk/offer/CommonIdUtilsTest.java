package com.mesosphere.sdk.offer;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link CommonIdUtils}.
 */
public class CommonIdUtilsTest {
    private static final String testTaskName = "test-task-name";

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(testTaskName + "__id");
        Assert.assertEquals(testTaskName, CommonIdUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonIdUtils.toTaskName(getTaskId(testTaskName + "_id"));
    }

    @Test
    public void testToExecutorId() {
        final String executorName = "dcos-0";

        final Protos.ExecutorID executorID = CommonIdUtils.toExecutorId(executorName);

        Assert.assertNotNull(executorID);
        final String value = executorID.getValue();
        Assert.assertTrue(StringUtils.isNotBlank(value));
        Assert.assertTrue(value.contains("__"));
        Assert.assertNotNull(UUID.fromString(value.split("__")[1]));
    }

    private static Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }
}
