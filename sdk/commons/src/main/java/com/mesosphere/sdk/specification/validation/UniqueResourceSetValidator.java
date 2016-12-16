package com.mesosphere.sdk.specification.validation;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.mesosphere.sdk.specification.ResourceSet;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Collection;
import java.util.HashSet;

/**
 * Unique Pod Type validator.
 */
public class UniqueResourceSetValidator implements ConstraintValidator<UniqueResourceSet, Collection<ResourceSet>> {
    @Override
    public void initialize(UniqueResourceSet constraintAnnotation) {
    }

    @Override
    public boolean isValid(Collection<ResourceSet> resourceSets, ConstraintValidatorContext constraintContext) {
        if (CollectionUtils.isEmpty(resourceSets)) {
            return true;
        }
        HashSet<String> resourceSetNames = new HashSet<>();

        for (ResourceSet resourceSet : resourceSets) {
            String id = resourceSet.getId();
            if (StringUtils.isEmpty(id)) {
                return false;
            } else if (resourceSetNames.contains(id)) {
                return false;
            } else {
                resourceSetNames.add(id);
            }
        }
        return true;
    }
}
