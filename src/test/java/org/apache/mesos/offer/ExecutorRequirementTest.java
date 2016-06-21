package org.apache.mesos.offer;

import static org.junit.Assert.*;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.junit.Test;

public class ExecutorRequirementTest {

    private static final ExecutorInfo VALID_EXECINFO = ExecutorInfo.newBuilder()
            .setExecutorId(ExecutorID.newBuilder().setValue(ResourceTestUtils.testExecutorId))
            .setName(ResourceTestUtils.testExecutorName)
            .setCommand(CommandInfo.newBuilder().build()) // ignored, required by proto
            .build();

    @Test
    public void testExecutorIdChanges() throws Exception {
        assertNotEquals(ResourceTestUtils.testExecutorId,
                new ExecutorRequirement(VALID_EXECINFO).getExecutorInfo().getExecutorId().getValue());
    }

    @Test
    public void testZeroResourcesValid() throws Exception {
        assertEquals(0, new ExecutorRequirement(VALID_EXECINFO).getResourceIds().size());
    }

    @Test
    public void testTwoResourcesValid() throws Exception {
        assertEquals(2, new ExecutorRequirement(VALID_EXECINFO.toBuilder()
                .addResources(ResourceBuilder.cpus(1.0))
                .addResources(ResourceBuilder.disk(1234.))
                .build()).getResourceRequirements().size());
    }

    @Test
    public void testEmptyExecIdValid() throws Exception {
        new ExecutorRequirement(VALID_EXECINFO.toBuilder()
                .setExecutorId(ExecutorID.newBuilder().setValue(""))
                .build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testBadExecIdFails() throws Exception {
        new ExecutorRequirement(VALID_EXECINFO.toBuilder()
                .setExecutorId(ExecutorID.newBuilder().setValue("foo"))
                .build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testMismatchNameFails() throws Exception {
        new ExecutorRequirement(VALID_EXECINFO.toBuilder().setName("asdf").build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testEmptyNameFails() throws Exception {
        new ExecutorRequirement(VALID_EXECINFO.toBuilder().setName("").build());
    }
}
