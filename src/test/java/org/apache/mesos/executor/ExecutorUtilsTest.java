package org.apache.mesos.executor;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class ExecutorUtilsTest {
    @Test
    public void testToExecutorId() {
        final String executorName = "dcos-0";

        final Protos.ExecutorID executorID = ExecutorUtils.toExecutorId(executorName);

        Assert.assertNotNull(executorID);
        final String value = executorID.getValue();
        Assert.assertTrue(StringUtils.isNotBlank(value));
        Assert.assertTrue(value.contains("__"));
        Assert.assertNotNull(UUID.fromString(value.split("__")[1]));
    }
}
