package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * This class tests Cassandra's custom replacement of nodes.
 */
public class CassandraRecoveryPlanOverriderTest extends BaseServiceSpecTest {
    private CassandraRecoveryPlanOverrider planOverrider;
    private StateStore stateStore;

    public CassandraRecoveryPlanOverriderTest() {
        super();
    }

    @Before
    public void beforeEach() throws Exception {
        super.beforeEach();
        stateStore = new StateStore(new MemPersister());
        ConfigStore<ServiceSpec> configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(getServiceSpec()),
                new MemPersister());
        UUID targetConfig = configStore.store(getServiceSpec());
        configStore.setTargetConfig(targetConfig);
        planOverrider = new CassandraRecoveryPlanOverrider(stateStore, getReplacePlan(stateStore, configStore));
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
        StateStoreUtils.storeTaskStatusAsProperty(
                stateStore,
                taskName,
                getFailedTaskStatus(taskName));
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
        StateStoreUtils.storeTaskStatusAsProperty(
                stateStore,
                taskName,
                getFailedTaskStatus(taskName));
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

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private Protos.TaskStatus getFailedTaskStatus(String taskId) {
        return Protos.TaskStatus.newBuilder()
                .setState(Protos.TaskState.TASK_FAILED)
                .setTaskId(Protos.TaskID.newBuilder().setValue(taskId))
                .setContainerStatus(
                        Protos.ContainerStatus.newBuilder()
                                .addNetworkInfos(Protos.NetworkInfo.newBuilder()
                                        .addIpAddresses(Protos.NetworkInfo.IPAddress.newBuilder()
                                                .setIpAddress("10.10.10.10"))))
                .build();
    }

    private ServiceSpec getServiceSpec() throws Exception {
        return getServiceSpec("svc.yml");
    }

    private RawServiceSpec getRawServiceSpec() throws Exception {
        return getRawServiceSpec("svc.yml");
    }

    private PodInstanceRequirement getRestartPodInstanceRequirement(int nodeIndex) throws Exception {
        return getPodInstanceRequirement(nodeIndex, RecoveryType.TRANSIENT);
    }

    private PodInstanceRequirement getReplacePodInstanceRequirement(int nodeIndex) throws Exception {
        return getPodInstanceRequirement(nodeIndex, RecoveryType.PERMANENT);
    }

    private PodInstanceRequirement getPodInstanceRequirement(int nodeIndex, RecoveryType recoveryType) throws Exception {
        PodSpec podSpec = getServiceSpec().getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, nodeIndex);
        return PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("server"))
                .recoveryType(recoveryType)
                .build();
    }

    private Plan getReplacePlan(StateStore stateStore, ConfigStore<ServiceSpec> configStore) throws Exception {
        final String REPLACE_PLAN_NAME = "replace";
        return new DefaultPlanGenerator(configStore, stateStore).generate(
                getRawServiceSpec().getPlans().get(REPLACE_PLAN_NAME),
                REPLACE_PLAN_NAME,
                getServiceSpec().getPods());
    }
}
