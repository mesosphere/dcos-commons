package com.mesosphere.sdk.config.validate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.mesosphere.sdk.offer.TaskUtils;
import org.apache.logging.log4j.util.Strings;

import com.mesosphere.sdk.specification.CommandSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

/**
 * Customizable configuration validator which requires that a specified task environment variable cannot be changed
 * after initial deployment.
 */
public class TaskEnvCannotChange implements ConfigValidator<ServiceSpec> {
    private final String podType;
    private final String taskName;
    private final String envName;
    private final Set<Rule> rules;

    /**
     * Variances to how enforcement should be performed. Omitting these rules results in disallowing any kind of change.
     */
    public enum Rule {
        // Allow the env value to be changed only if it was originally missing or empty.
        ALLOW_UNSET_TO_SET,

        // Allow the env value to be cleared if it was originally set
        ALLOW_SET_TO_UNSET
    }

    /**
     * Creates a new validator restricting changes to an environment value.
     *
     * @param podType The type of the pod to enforce (see {@link PodSpec#getType()})
     * @param taskName The name of the task to enforce (see {@link TaskSpec#getName()})
     * @param envName The environment variable to restrict within the above pod/task
     * @param rules A list of any allowances to be made in the enforcement
     */
    public TaskEnvCannotChange(String podType, String taskName, String envName, Rule... rules) {
        this.podType = podType;
        this.taskName = taskName;
        this.envName = envName;
        this.rules = new HashSet<>(Arrays.asList(rules));
    }

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!oldConfig.isPresent()) {
            return Collections.emptyList();
        }

        Optional<TaskSpec> oldTask = TaskUtils.getTaskSpec(oldConfig.get(), podType, taskName);
        if (!oldTask.isPresent()) {
            // Maybe the pod or task was renamed? Lets avoid enforcing whether those are rename- able and assume it's OK
            return Collections.emptyList();
        }
        if (!oldTask.get().getCommand().isPresent()) {
            // Similar to above: assume that the command was recently added to this task.
            return Collections.emptyList();
        }

        Optional<TaskSpec> newTask = TaskUtils.getTaskSpec(newConfig, podType, taskName);
        if (!newTask.isPresent()) {
            throw new IllegalArgumentException(String.format("Unable to find requested pod=%s, task=%s in config: %s",
                    podType, taskName, newConfig));
        }
        if (!newTask.get().getCommand().isPresent()) {
            throw new IllegalArgumentException(String.format(
                    "Requested pod=%s, task=%s in config lacks a command section: %s",
                    podType, taskName, newTask.get()));
        }

        Optional<ConfigValidationError> error =
                validateEnvChange(oldTask.get().getCommand().get(), newTask.get().getCommand().get());
        return error.isPresent() ? Arrays.asList(error.get()) : Collections.emptyList();
    }

    private Optional<ConfigValidationError> validateEnvChange(CommandSpec oldCommand, CommandSpec newCommand) {
        final String oldEnvVal = oldCommand.getEnvironment().get(envName);
        final String newEnvVal = newCommand.getEnvironment().get(envName);

        if (Strings.isBlank(oldEnvVal)) {
            if (Strings.isBlank(newEnvVal)) {
                // <unset> to <unset> (no change)
                return Optional.empty();
            } else {
                // <unset> to SomeVal (added)
                if (rules.contains(Rule.ALLOW_UNSET_TO_SET)) {
                    return Optional.empty();
                }
                return Optional.of(ConfigValidationError.transitionError(
                        String.format("%s.%s.env.%s", podType, taskName, envName),
                        oldEnvVal, newEnvVal,
                        String.format("Env value %s cannot change from unset to set", envName)));
            }
        } else {
            if (Strings.isBlank(newEnvVal)) {
                // SomeVal to <unset> (removed)
                if (rules.contains(Rule.ALLOW_SET_TO_UNSET)) {
                    return Optional.empty();
                }
                return Optional.of(ConfigValidationError.transitionError(
                        String.format("%s.%s.env.%s", podType, taskName, envName),
                        oldEnvVal, newEnvVal,
                        String.format("Env value %s cannot be unset after being set", envName)));
            } else if (oldEnvVal.equals(newEnvVal)) {
                // SomeVal to SomeVal (no change)
                return Optional.empty();
            } else {
                // SomeVal to SomeVal2 (changed)
                return Optional.of(ConfigValidationError.transitionError(
                        String.format("%s.%s.env.%s", podType, taskName, envName),
                        oldEnvVal, newEnvVal,
                        String.format("Env value %s cannot change", envName)));
            }
        }
    }
}
