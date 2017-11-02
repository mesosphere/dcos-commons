package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.CommandSpec;
import com.mesosphere.sdk.specification.DefaultCommandSpec;
import com.mesosphere.sdk.specification.DefaultTaskSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.GoalStateOverride;
import org.apache.mesos.Protos;

import java.util.Collections;
import java.util.Map;

/**
 * The PodOverrideVisitor traverses a {@link PodSpec} and replaces task definitions with the override definition
 * specified in the state store.
 */
public class PodOverrideVisitor extends NullVisitor<EvaluationOutcome> {
    private final Map<TaskSpec, GoalStateOverride> goalStateOverrides;
    private final SchedulerConfig schedulerConfig;

    public PodOverrideVisitor(
            Map<TaskSpec, GoalStateOverride> goalStateOverrides,
            SchedulerConfig schedulerConfig,
            SpecVisitor delegate) {
        super(delegate);

        this.goalStateOverrides = goalStateOverrides;
        this.schedulerConfig = schedulerConfig;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) throws SpecVisitorException {
        if (!goalStateOverrides.containsKey(taskSpec) ||
                !goalStateOverrides.get(taskSpec).equals(GoalStateOverride.PAUSED)) {
            return taskSpec;
        }

        Map<String, String> environment;
        if (taskSpec.getCommand().isPresent()) {
            environment = taskSpec.getCommand().get().getEnvironment();
        } else {
            environment = Collections.emptyMap();
        }

        CommandSpec commandSpec = new DefaultCommandSpec(schedulerConfig.getPauseOverrideCmd(), environment) {
            @Override
            public Protos.CommandInfo.Builder toProto() {
                Protos.CommandInfo.Builder commandBuilder = super.toProto();
                commandBuilder.addUrisBuilder().setValue(SchedulerConfig.fromEnv().getBootstrapURI());

                return commandBuilder;
            }
        };

        return DefaultTaskSpec.newBuilder(taskSpec).commandSpec(commandSpec).build();
    }
}
