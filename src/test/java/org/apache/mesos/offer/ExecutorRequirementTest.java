package org.apache.mesos.offer;

import static org.junit.Assert.*;

import org.apache.mesos.Protos;
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
    public void testExecutorIdRemainsSame() throws Exception {
        assertEquals(ResourceTestUtils.testExecutorId,
                ExecutorRequirement.create(VALID_EXECINFO).getExecutorInfo().getExecutorId().getValue());
    }

    @Test(expected=InvalidRequirementException.class)
    public void testRejectDesiredResourcesForExistingExecutor() throws Exception {
        Protos.Resource desiredCpu = ResourceUtils.getDesiredScalar("test-role", "test-prinicipal", "cpus", 1.0);
        ExecutorInfo invalidExecInfo = ExecutorInfo.newBuilder()
                .setExecutorId(ExecutorID.newBuilder().setValue(ResourceTestUtils.testExecutorId))
                .setName(ResourceTestUtils.testExecutorName)
                .setCommand(CommandInfo.newBuilder().build()) // ignored, required by proto
                .addResources(desiredCpu)
                .build();
        ExecutorRequirement.create(invalidExecInfo);
    }

    @Test
    public void testCreateExistingExecutorRequirement() throws Exception {
        Protos.Resource expectedCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0);
        ExecutorInfo validExecInfo = ExecutorInfo.newBuilder(VALID_EXECINFO)
                .addResources(expectedCpu)
                .build();
        assertNotNull(ExecutorRequirement.create(validExecInfo));
    }

    @Test
    public void testZeroResourcesValid() throws Exception {
        assertEquals(0, ExecutorRequirement.create(VALID_EXECINFO).getResourceIds().size());
    }

    @Test
    public void testTwoResourcesValid() throws Exception {
        assertEquals(2, ExecutorRequirement.create(VALID_EXECINFO.toBuilder()
                .addResources(ResourceBuilder.cpus(1.0))
                .addResources(ResourceBuilder.disk(1234.))
                .build()).getResourceRequirements().size());
    }

    @Test
    public void testEmptyExecIdValid() throws Exception {
        ExecutorRequirement.create(VALID_EXECINFO.toBuilder()
                .setExecutorId(ExecutorID.newBuilder().setValue(""))
                .build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testBadExecIdFails() throws Exception {
        ExecutorRequirement.create(VALID_EXECINFO.toBuilder()
                .setExecutorId(ExecutorID.newBuilder().setValue("foo"))
                .build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testMismatchNameFails() throws Exception {
        ExecutorRequirement.create(VALID_EXECINFO.toBuilder().setName("asdf").build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testEmptyNameFails() throws Exception {
        ExecutorRequirement.create(VALID_EXECINFO.toBuilder().setName("").build());
    }
}
