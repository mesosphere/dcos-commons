package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ExecutorRequirementTest {

    private static final ExecutorInfo VALID_EXECINFO = ExecutorInfo.newBuilder()
            .setExecutorId(TestConstants.EXECUTOR_ID)
            .setName(TestConstants.EXECUTOR_NAME)
            .setCommand(CommandInfo.newBuilder().build()) // ignored, required by proto
            .build();

    @Test
    public void testExecutorIdRemainsSame() throws Exception {
        assertEquals(
                TestConstants.EXECUTOR_ID,
                ExecutorRequirement.create(VALID_EXECINFO).getExecutorInfo().getExecutorId());
    }

    @Test(expected=InvalidRequirementException.class)
    public void testRejectDesiredResourcesForExistingExecutor() throws Exception {
        Protos.Resource desiredCpu = ResourceUtils.getDesiredScalar("test-role", "test-prinicipal", "cpus", 1.0);
        ExecutorInfo invalidExecInfo = ExecutorInfo.newBuilder()
                .setExecutorId(TestConstants.EXECUTOR_ID)
                .setName(TestConstants.EXECUTOR_NAME)
                .setCommand(CommandInfo.newBuilder().build()) // ignored, required by proto
                .addResources(desiredCpu)
                .build();
        ExecutorRequirement.create(invalidExecInfo);
    }

    @Test
    public void testCreateExistingExecutorRequirement() throws Exception {
        Protos.Resource expectedCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, UUID.randomUUID().toString());
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
                .addResources(ResourceUtils.getUnreservedScalar("cpus", 1.0))
                .addResources(ResourceUtils.getUnreservedScalar("disk", 1234.0))
                .build()).getResourceRequirements().size());
    }

    @Test
    public void testEmptyExecIdValid() throws Exception {
        ExecutorRequirement.create(VALID_EXECINFO.toBuilder()
                .setExecutorId(TaskUtils.emptyExecutorId())
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
