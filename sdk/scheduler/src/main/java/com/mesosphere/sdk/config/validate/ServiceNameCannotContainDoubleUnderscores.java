package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.*;

/**
 * Sample configuration validator which validates that a ServiceSpecification's number of PodSpecs
 * and number of tasks within those PodSpecs never go down.
 */
public class ServiceNameCannotContainDoubleUnderscores implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (newConfig.getName().contains("__")) {
            return Arrays.asList(ConfigValidationError.valueError(
                    "ServiceName",
                    newConfig.getName(),
                    String.format("Service name may not contain double underscores: %s", newConfig.getName())));
        }
        return Collections.emptyList();
    }
}
