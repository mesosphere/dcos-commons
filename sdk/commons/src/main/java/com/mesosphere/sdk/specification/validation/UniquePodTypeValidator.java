package com.mesosphere.sdk.specification.validation;

import org.apache.commons.lang3.StringUtils;
import com.mesosphere.sdk.specification.PodSpec;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;

/**
 * Defines validator for UniquePodType annotation.
 */
public class UniquePodTypeValidator implements ConstraintValidator<UniquePodType, List<PodSpec>> {
    @Override
    public void initialize(UniquePodType constraintAnnotation) {
    }

    @Override
    public boolean isValid(List<PodSpec> podSpecs, ConstraintValidatorContext constraintContext) {
        HashSet<String> podTypes = new HashSet<>();

        for (PodSpec podSpec : podSpecs) {
            String id = podSpec.getType();
            if (StringUtils.isEmpty(id)) {
                return false;
            } else if (podTypes.contains(id)) {
                return false;
            } else {
                podTypes.add(id);
            }
        }
        return true;
    }
}
