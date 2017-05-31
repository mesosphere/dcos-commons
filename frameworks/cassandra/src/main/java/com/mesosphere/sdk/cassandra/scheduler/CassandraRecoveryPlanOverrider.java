package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryStep;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverrider;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.specification.CommandSpec;
import com.mesosphere.sdk.specification.DefaultCommandSpec;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultTaskSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The CassandraRecoveryPlanManager handles failure scenarios unique to Cassandra. It falls back to the default recovery
 * behavior when appropriate.
 */
public class CassandraRecoveryPlanOverrider implements RecoveryPlanOverrider {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String RECOVERY_PHASE_NAME = "permanent-node-failure-recovery";
    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;
    private final Plan replaceNodePlan;

    public CassandraRecoveryPlanOverrider(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            Plan replaceNodePlan) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.replaceNodePlan = replaceNodePlan;
    }

    @Override
    public Optional<Phase> override(PodInstanceRequirement stoppedPod) {
        if (!stoppedPod.getPodInstance().getPod().getType().equals("node")
                || stoppedPod.getRecoveryType() != RecoveryType.PERMANENT) {
            logger.info("No overrides necessary. Pod is not a node or it isn't a permanent failure.");
            return Optional.empty();
        }

        Phase nnPhase = null;
        int index = stoppedPod.getPodInstance().getIndex();
        logger.info(String.format("Returning replacement plan for node %d.", index));
        return Optional.ofNullable(getNodeRecoveryPhase(replaceNodePlan, index));
    }

    private Phase getNodeRecoveryPhase(Plan inputPlan, int index) {
        // Get IP address for replacement node.
        Optional<Protos.TaskStatus> statusOptional = stateStore.fetchStatus(String.format("node-%d-server", index));

        if (!statusOptional.isPresent()) {
            logger.error("Task {} scheduled for recovery, but doesn't exist");
            return null;
        }

        Protos.TaskStatus status = statusOptional.get();
        String replaceIp = status.getContainerStatus()
                .getNetworkInfosList().get(0)
                .getIpAddressesList().get(0)
                .getIpAddress();

        Phase inputPhase = inputPlan.getChildren().get(0);
        Step inputLaunchStep = inputPhase.getChildren().get(index);

        // Dig all the way down into the command, so we can append the replace_address option to it.
        PodInstance podInstance = inputLaunchStep.start().get().getPodInstance();
        PodSpec podSpec = podInstance.getPod();
        TaskSpec taskSpec = podSpec.getTasks().stream().filter(t -> t.getName().equals("server")).findFirst().get();
        CommandSpec command = taskSpec.getCommand().get();

        DefaultCommandSpec.Builder builder = DefaultCommandSpec.newBuilder(command);
        builder.value(String.format(
                "%s -Dcassandra.replace_address=%s -Dcassandra.consistent.rangemovement=false%n",
                command.getValue().trim(), replaceIp));

        // Rebuild a new PodSpec with the modified command, and add it to the phase we return.
        TaskSpec newTaskSpec = DefaultTaskSpec.newBuilder(taskSpec).commandSpec(builder.build()).build();
        List<TaskSpec> tasks = podSpec.getTasks().stream()
                .map(t -> {
                    if (t.getName().equals(newTaskSpec.getName())) {
                        return newTaskSpec;
                    }
                    return t;
                })
                .collect(Collectors.toList());
        PodSpec newPodSpec = DefaultPodSpec.newBuilder(podSpec).tasks(tasks).build();
        PodInstance newPodInstance = new DefaultPodInstance(newPodSpec, index);

        PodInstanceRequirement replacePodInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                    newPodInstance, inputLaunchStep.start().get().getTasksToLaunch())
                .recoveryType(RecoveryType.PERMANENT)
                .build();

        Step replaceStep = new DefaultRecoveryStep(
                inputLaunchStep.getName(),
                Status.PENDING,
                replacePodInstanceRequirement,
                new UnconstrainedLaunchConstrainer(),
                stateStore);

        return new DefaultPhase(
                RECOVERY_PHASE_NAME, Arrays.asList(replaceStep), new SerialStrategy<>(), Collections.emptyList());
    }
}
