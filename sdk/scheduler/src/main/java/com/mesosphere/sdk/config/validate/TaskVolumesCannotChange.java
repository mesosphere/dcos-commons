package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.*;

import java.util.*;

/**
 * Validates that each TaskSpecification's volumes have not been modified.
 */
public class TaskVolumesCannotChange implements ConfigurationValidator<ServiceSpec> {


    private static Map<String, TaskSpec> getAllTasksByName(
            ServiceSpec service, List<ConfigurationValidationError> errors) {
        Map<String, TaskSpec> tasks = new HashMap<>();
        for (PodSpec podSpec : service.getPods()) {
            for (TaskSpec taskSpec : podSpec.getTasks()) {
                TaskSpec priorTask = tasks.put(podSpec.getType() + "-" + taskSpec.getName(), taskSpec);
                if (priorTask != null) {
                    errors.add(ConfigurationValidationError.valueError(
                            "TaskSpecifications", taskSpec.getName(),
                            String.format("Duplicate TaskSpecifications named '%s' in Service '%s': %s %s",
                                    taskSpec.getName(), service.getName(), priorTask, taskSpec)));
                }
            }
        }

        return tasks;
    }

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        Map<String, TaskSpec> oldTasks =
                (nullableOldConfig == null) ? new HashMap<>() : getAllTasksByName(nullableOldConfig, errors);
        Map<String, TaskSpec> newTasks = getAllTasksByName(newConfig, errors);

        // Note: We're intentionally just comparing cases where the tasks in both TaskSpecs.
        // Enforcement of new/removed tasks should be performed in a separate Validator.
        for (Map.Entry<String, TaskSpec> oldEntry : oldTasks.entrySet()) {
            TaskSpec newTask = newTasks.get(oldEntry.getKey());
            if (newTask == null) {
                // Task removed: Don't worry about it. We're just verifying that volumes don't change.
                continue;
            }

            if (!TaskUtils.volumesEqual(oldEntry.getValue(), newTask)) {
                errors.add(ConfigurationValidationError.transitionError(
                        String.format("TaskVolumes[taskname:%s]", newTask.getName()),
                        oldEntry.getValue().getResourceSet().getVolumes().toString(),
                        newTask.getResourceSet().getVolumes().toString(),
                        "Volumes must be equal."));
            }
        }

        return errors;
    }
}
