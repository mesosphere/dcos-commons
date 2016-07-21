package org.apache.mesos.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Default Configuration validator, which validates a given new {@code Configuration} w.r.t.
 * an old {@code Configuration}.
 */
public class DefaultConfigurationValidator {
    private final Collection<ConfigurationValidation> validations = new ArrayList<>();

    public DefaultConfigurationValidator(final ConfigurationValidation... validations) {
        this(Arrays.asList(validations));
    }

    public DefaultConfigurationValidator(final Collection<ConfigurationValidation> validations) {
        if (validations != null && !validations.isEmpty()) {
            this.validations.addAll(validations);
        }
    }

    public Collection<ConfigurationValidationError> validate(Configuration oldConfig, Configuration newConfig) {
        final List<ConfigurationValidationError> errors = new ArrayList<>();
        for (ConfigurationValidation validation : validations) {
            errors.addAll(validation.validate(oldConfig, newConfig));
        }
        return errors;
    }
}
