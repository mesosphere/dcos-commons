package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.scheduler.plan.DefaultPlanGenerator;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.TaskStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.ServiceTestResult;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * This class tests Cassandra's custom replacement of nodes.
 */
public class CassandraRecoveryPlanOverriderTest {

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final Optional<String> IP_VALUE = Optional.of("10.10.10.10");

    private final RawServiceSpec rawSpec;
    private final ServiceSpec serviceSpec;

    private CassandraRecoveryPlanOverrider planOverrider;
    private TaskStore mockTaskStore;

    public CassandraRecoveryPlanOverriderTest() throws Exception {
        ServiceTestResult result = new ServiceTestRunner()
                .setPodEnv("node", "LOCAL_SEEDS", "foo,bar")
                .run();
        rawSpec = result.getRawServiceSpec();
        serviceSpec = result.getServiceSpec();
    }

    @Before
    public void beforeEach() throws Exception {
        mockTaskStore = Mockito.mock(TaskStore.class);
        ConfigStore<ServiceSpec> configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec),
                new MemPersister());
        UUID targetConfig = configStore.store(serviceSpec);
        configStore.setTargetConfig(targetConfig);
        planOverrider = new CassandraRecoveryPlanOverrider(mockTaskStore, getReplacePlan(configStore));
    }

    @Test
    public void ovverrideNoOp() throws Exception {
        PodInstanceRequirement podInstanceRequirement = getRestartPodInstanceRequirement(0);
        Optional<Phase> phase = planOverrider.override(podInstanceRequirement);
        Assert.assertFalse(phase.isPresent());
    }

    @Test
    public void replaceNonSeed() throws Exception {
        int nonSeedIndex = 2;
        String taskName = "node-" + nonSeedIndex + "-server";
        when(mockTaskStore.getTaskIp(taskName)).thenReturn(IP_VALUE);
        PodInstanceRequirement podInstanceRequirement = getReplacePodInstanceRequirement(nonSeedIndex);
        Optional<Phase> phase = planOverrider.override(podInstanceRequirement);
        Assert.assertTrue(phase.isPresent());
        Assert.assertEquals(1, phase.get().getChildren().size());
        Assert.assertEquals(
                RecoveryType.PERMANENT,
                phase.get().getChildren().get(0).getPodInstanceRequirement().get().getRecoveryType());
    }

    @Test
    public void replaceSeed() throws Exception {
        int nonSeedIndex = 1;
        String taskName = "node-" + nonSeedIndex + "-server";
        when(mockTaskStore.getTaskIp(taskName)).thenReturn(IP_VALUE);
        PodInstanceRequirement podInstanceRequirement = getReplacePodInstanceRequirement(nonSeedIndex);
        Optional<Phase> phase = planOverrider.override(podInstanceRequirement);
        Assert.assertTrue(phase.isPresent());
        Assert.assertEquals(3, phase.get().getChildren().size());
        Assert.assertEquals(
                RecoveryType.PERMANENT,
                phase.get().getChildren().get(0).getPodInstanceRequirement().get().getRecoveryType());
        Assert.assertEquals(
                RecoveryType.TRANSIENT,
                phase.get().getChildren().get(1).getPodInstanceRequirement().get().getRecoveryType());
        Assert.assertEquals(
                RecoveryType.TRANSIENT,
                phase.get().getChildren().get(2).getPodInstanceRequirement().get().getRecoveryType());
    }

    private PodInstanceRequirement getRestartPodInstanceRequirement(int nodeIndex) throws Exception {
        return getPodInstanceRequirement(nodeIndex, RecoveryType.TRANSIENT);
    }

    private PodInstanceRequirement getReplacePodInstanceRequirement(int nodeIndex) throws Exception {
        return getPodInstanceRequirement(nodeIndex, RecoveryType.PERMANENT);
    }

    private PodInstanceRequirement getPodInstanceRequirement(int nodeIndex, RecoveryType recoveryType) throws Exception {
        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, nodeIndex);
        return PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("server"))
                .recoveryType(recoveryType)
                .build();
    }

    private Plan getReplacePlan(ConfigStore<ServiceSpec> configStore) throws Exception {
        return new DefaultPlanGenerator(configStore, new StateStore(new MemPersister()))
                .generate(rawSpec.getPlans().get("replace"), "replace", serviceSpec.getPods());
    }
}
