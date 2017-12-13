package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

/**
 * This class tests {@link DefaultTaskSpec}.
 */
public class DefaultTaskSpecTest {
    @Test
    public void cloneTaskSpec() {
        DefaultTaskSpec original = new DefaultTaskSpec(
                "task",
                GoalState.RUNNING,
                false,
                new DefaultResourceSet(
                        "rs-id",
                        Arrays.asList(
                                new DefaultResourceSpec(
                                        "cpus",
                                        Protos.Value.newBuilder()
                                                .setType(Protos.Value.Type.SCALAR)
                                                .setScalar(Protos.Value.Scalar.newBuilder()
                                                        .setValue(1.0))
                                                .build(),
                                        TestConstants.ROLE,
                                        TestConstants.PRE_RESERVED_ROLE,
                                        TestConstants.PRINCIPAL)),
                        Arrays.asList(
                                new DefaultVolumeSpec(
                                        100,
                                        VolumeSpec.Type.ROOT,
                                        TestConstants.CONTAINER_PATH,
                                        TestConstants.ROLE,
                                        TestConstants.PRE_RESERVED_ROLE,
                                        TestConstants.PRINCIPAL)),
                        TestConstants.ROLE,
                        TestConstants.PRE_RESERVED_ROLE,
                        TestConstants.PRINCIPAL),
                new DefaultCommandSpec("./cmd", new HashMap<>()),
                new DefaultHealthCheckSpec("./health-check", 1, 1, 1, 1, 1),
                new DefaultReadinessCheckSpec("./readiness-check", 2, 2, 2),
                Arrays.asList(
                        new DefaultConfigFileSpec("name", "relative-path", "template-content")),
                new DefaultDiscoverySpec("prefix", Protos.DiscoveryInfo.Visibility.CLUSTER),
                DefaultTaskSpec.TASK_KILL_GRACE_PERIOD_SECONDS_DEFAULT,
                null);

        DefaultTaskSpec clone = DefaultTaskSpec.newBuilder(original).build();
        Assert.assertEquals(original, clone);
    }
}
