package com.mesosphere.sdk.specification.validation;

import org.apache.mesos.Protos;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Ensure that a scalar Proto's value is > 0.
 */
public class NonZeroScalarProtoValueValidator implements
        ConstraintValidator<NonZeroScalarProtoValue, Protos.Value> {
    @Override
    public void initialize(NonZeroScalarProtoValue nonZeroScalarProtoValue) {
    }

    @Override
    public boolean isValid(Protos.Value value,
                           ConstraintValidatorContext context) {
        return value != null // Not valid if null.
                && (value.hasRanges()  // Ignore Ranges.
                || (value.hasScalar() && value.getScalar().getValue() > 0));
    }
}
