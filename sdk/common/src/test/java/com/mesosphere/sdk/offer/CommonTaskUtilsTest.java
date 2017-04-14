package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the CommonTaskUtils class.
 */
public class CommonTaskUtilsTest {
    private static final String testTaskName = "test-task-name";

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(testTaskName + "__id");
        Assert.assertEquals(testTaskName, CommonTaskUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonTaskUtils.toTaskName(getTaskId(testTaskName + "_id"));
    }

    private static Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }
}
