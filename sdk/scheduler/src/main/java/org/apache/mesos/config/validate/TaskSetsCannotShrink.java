package org.apache.mesos.config.validate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;

/**
 * Sample configuration validator which validates that a ServiceSpecification's number of TaskSets
 * and number of tasks within those TaskSets never go down.
 */
public class TaskSetsCannotShrink implements ConfigurationValidator<ServiceSpecification> {

    @Override
    public Collection<ConfigurationValidationError> validate(
            ServiceSpecification nullableConfig, ServiceSpecification newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        Map<String, Integer> newTaskTypeCounts = new HashMap<>();
        for (TaskSet taskSet : newConfig.getTaskSets()) {
            Integer prevValue = newTaskTypeCounts.put(
                    taskSet.getName(), taskSet.getTaskSpecifications().size());
            if (prevValue != null) {
                errors.add(ConfigurationValidationError.valueError(
                        "TaskSets", taskSet.getName(),
                        String.format("Duplicate TaskSets named '%s' in Service '%s'",
                                taskSet.getName(), newConfig.getName())));
            }
        }
        if (nullableConfig == null) {
            // No sizes to compare.
            return errors;
        }
        // Check for TaskSets in the old config which are missing or smaller in the new config.
        // Adding new TaskSets or increasing the size of tasksets are allowed.
        for (TaskSet oldTaskSet : nullableConfig.getTaskSets()) {
            final String typeName = oldTaskSet.getName();
            int oldValue = oldTaskSet.getTaskSpecifications().size();
            Integer newValue = newTaskTypeCounts.get(typeName);
            if (newValue == null) {
                errors.add(ConfigurationValidationError.transitionError(
                        String.format("TaskSet[name:%s]", typeName),
                        String.valueOf(oldValue),
                        "null",
                        String.format("New config is missing TaskSet named '%s' (expected present with >= %d tasks)",
                                typeName, oldValue)));
            } else if (newValue < oldValue) {
                errors.add(ConfigurationValidationError.transitionError(
                        String.format("TaskSet[setname:%s]", typeName),
                        String.valueOf(oldValue),
                        String.valueOf(newValue),
                        String.format("New config's TaskSet named '%s' has %d tasks, expected >=%d tasks",
                                typeName, newValue, oldValue)));
            }
        }
        return errors;
    }
}
