package org.apache.mesos.config.validate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecification;

/**
 * Validates that each TaskSpecification's volumes have not been modified.
 */
public class TaskVolumesCannotChange implements ConfigurationValidator<ServiceSpecification> {

    @Override
    public Collection<ConfigurationValidationError> validate(
            ServiceSpecification oldConfig, ServiceSpecification newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        Map<String, TaskSpecification> oldTasks = getAllTasksByName(oldConfig, errors);
        Map<String, TaskSpecification> newTasks = getAllTasksByName(newConfig, errors);
        // Note: We're itentionally just comparing cases where the tasks in both TaskSpecs.
        // Enforcement of new/removed tasks should be performed in a separate Validator.
        for (Map.Entry<String, TaskSpecification> oldEntry : oldTasks.entrySet()) {
            TaskSpecification newTask = newTasks.get(oldEntry.getKey());
            if (newTask == null) {
                // Task removed: Don't worry about it. We're just verifying that volumes don't change.
                continue;
            }
            if (!CollectionUtils.isEqualCollection(
                    oldEntry.getValue().getVolumes(),
                    newTask.getVolumes())) {
                errors.add(ConfigurationValidationError.transitionError(
                        String.format("TaskVolumes[taskname:%s]", newTask.getName()),
                        oldEntry.getValue().getVolumes().toString(),
                        newTask.getVolumes().toString(),
                        "Volumes must be equal."));
            }
        }
        return errors;
    }

    private static Map<String, TaskSpecification> getAllTasksByName(
            ServiceSpecification service, List<ConfigurationValidationError> errors) {
        Map<String, TaskSpecification> tasks = new HashMap<>();
        for (TaskSet taskSet : service.getTaskSets()) {
            for (TaskSpecification taskSpecification : taskSet.getTaskSpecifications()) {
                TaskSpecification priorTask = tasks.put(taskSpecification.getName(), taskSpecification);
                if (priorTask != null) {
                    errors.add(ConfigurationValidationError.valueError(
                            "TaskSpecifications", taskSpecification.getName(),
                            String.format("Duplicate TaskSpecifications named '%s' in Service '%s': %s %s",
                                    taskSpecification.getName(), service.getName(), priorTask, taskSpecification)));
                }
            }
        }
        return tasks;
    }
}
