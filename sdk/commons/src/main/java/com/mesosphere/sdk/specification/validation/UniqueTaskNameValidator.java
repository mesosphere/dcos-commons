package com.mesosphere.sdk.specification.validation;

import org.apache.commons.lang3.StringUtils;
import com.mesosphere.sdk.specification.TaskSpec;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;

/**
 * Defines validator for UniqueTaskName annotation.
 */
public class UniqueTaskNameValidator implements ConstraintValidator<UniqueTaskName, List<TaskSpec>> {
    @Override
    public void initialize(UniqueTaskName constraintAnnotation) {
    }

    @Override
    public boolean isValid(List<TaskSpec> taskSpecs, ConstraintValidatorContext constraintContext) {
        HashSet<String> taskNames = new HashSet<>();

        for (TaskSpec taskSpec : taskSpecs) {
            String taskSpecName = taskSpec.getName();
            if (StringUtils.isEmpty(taskSpecName)) {
                return false;
            } else if (taskNames.contains(taskSpecName)) {
                return false;
            } else {
                taskNames.add(taskSpecName);
            }
        }
        return true;
    }
}
