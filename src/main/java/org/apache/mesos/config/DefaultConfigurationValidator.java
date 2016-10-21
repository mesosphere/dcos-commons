package org.apache.mesos.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Default {@link Configuration} validator, which validates the transition from an old
 * {@code Configuration} to a new {@code Configuration}.
 *
 * @param <C> the type of configuration to be validated
 */
public class DefaultConfigurationValidator<C extends Configuration> {
    private final Collection<ConfigurationValidation<C>> validations = new ArrayList<>();

    @SafeVarargs
    public DefaultConfigurationValidator(final ConfigurationValidation<C>... validations) {
        this(Arrays.asList(validations));
    }

    public DefaultConfigurationValidator(final Collection<ConfigurationValidation<C>> validations) {
        this.validations.addAll(validations);
    }

    public Collection<ConfigurationValidationError> validate(C oldConfig, C newConfig) {
        final List<ConfigurationValidationError> errors = new ArrayList<>();
        for (ConfigurationValidation<C> validation : validations) {
            errors.addAll(validation.validate(oldConfig, newConfig));
        }
        return errors;
    }
}
