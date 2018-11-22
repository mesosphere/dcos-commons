package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.testutils.TestConstants;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * This class tests {@link DefaultTaskSpec}.
 */
public class DefaultTaskSpecTest {
    @Test
    public void cloneTaskSpec() {
        DefaultTaskSpec original = DefaultTaskSpec.newBuilder()
                .name("task")
                .goalState(GoalState.RUNNING)
                .essential(false)
                .resourceSet(DefaultResourceSet.newBuilder(TestConstants.ROLE,
                        TestConstants.PRE_RESERVED_ROLE,
                        TestConstants.PRINCIPAL)
                        .id("rs-id")
                        .addResource(new DefaultResourceSpec(
                                "cpus",
                                Protos.Value.newBuilder()
                                        .setType(Protos.Value.Type.SCALAR)
                                        .setScalar(Protos.Value.Scalar.newBuilder()
                                                .setValue(1.0))
                                        .build(),
                                TestConstants.ROLE,
                                TestConstants.PRE_RESERVED_ROLE,
                                TestConstants.PRINCIPAL))
                        .volumes(Collections.singleton(DefaultVolumeSpec.createRootVolume(
                                100,
                                TestConstants.CONTAINER_PATH,
                                TestConstants.ROLE,
                                TestConstants.PRE_RESERVED_ROLE,
                                TestConstants.PRINCIPAL)))
                        .build())
                .commandSpec(DefaultCommandSpec.newBuilder(Collections.emptyMap()).value("./cmd").build())
                .healthCheckSpec(DefaultHealthCheckSpec.newBuilder()
                        .command("./health-check")
                        .maxConsecutiveFailures(1)
                        .delay(1)
                        .interval(1)
                        .timeout(1)
                        .gracePeriod(1)
                        .build())
                .readinessCheckSpec(DefaultReadinessCheckSpec.newBuilder("./readiness-check", 2, 2)
                        .delay(2)
                        .build())
                .configFiles(Collections.singleton(DefaultConfigFileSpec.newBuilder()
                        .name("name")
                        .relativePath("relative-path")
                        .templateContent("template-content")
                        .build()))
                .discoverySpec(DefaultDiscoverySpec.newBuilder()
                        .prefix("prefix")
                        .visibility(Protos.DiscoveryInfo.Visibility.CLUSTER)
                        .build())
                .taskKillGracePeriodSeconds(DefaultTaskSpec.TASK_KILL_GRACE_PERIOD_SECONDS_DEFAULT)
                .build();

        DefaultTaskSpec clone = DefaultTaskSpec.newBuilder(original).build();
        Assert.assertEquals(original, clone);
    }
}
