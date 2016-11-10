package org.apache.mesos.config.validate;

import org.apache.mesos.specification.PodSpec;
import org.apache.mesos.specification.ServiceSpec;

import java.util.*;

/**
 * Sample configuration validator which validates that a ServiceSpecification's number of PodSpecs
 * and number of tasks within those PodSpecs never go down.
 */
public class PodSpecsCannotShrink implements ConfigurationValidator<ServiceSpec> {

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        Map<String, Integer> newTaskTypeCounts = new HashMap<>();
        for (PodSpec podSpec : newConfig.getPods()) {
            Integer prevValue = newTaskTypeCounts.put(podSpec.getType(), podSpec.getTasks().size());
            if (prevValue != null) {
                errors.add(ConfigurationValidationError.valueError(
                        "PodSpec", podSpec.getType(),
                        String.format("Duplicate PodSpecs named '%s' in Service '%s'",
                                podSpec.getType(), newConfig.getName())));
            }
        }

        if (nullableOldConfig == null) {
            // No sizes to compare.
            return errors;
        }

        // Check for PodSpecs in the old config which are missing or smaller in the new config.
        // Adding new PodSpecs or increasing the size of tasksets are allowed.
        for (PodSpec podSpec : nullableOldConfig.getPods()) {
            final String typeName = podSpec.getType();
            int oldValue = podSpec.getTasks().size();
            Integer newValue = newTaskTypeCounts.get(typeName);
            if (newValue == null) {
                errors.add(ConfigurationValidationError.transitionError(
                        String.format("PodSpec[name:%s]", typeName),
                        String.valueOf(oldValue),
                        "null",
                        String.format("New config is missing PodSpec named '%s' (expected present with >= %d tasks)",
                                typeName, oldValue)));
            } else if (newValue < oldValue) {
                errors.add(ConfigurationValidationError.transitionError(
                        String.format("PodSpec[setname:%s]", typeName),
                        String.valueOf(oldValue),
                        String.valueOf(newValue),
                        String.format("New config's PodSpec named '%s' has %d tasks, expected >=%d tasks",
                                typeName, newValue, oldValue)));
            }
        }
        return errors;
    }
}
