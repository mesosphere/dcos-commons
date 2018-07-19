package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.config.validate.ZoneValidator;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.Collection;
import java.util.Optional;

/**
 * This class validates that the DETECT_ZONES envvar can only support the following transitions.
 *
 * 1. null -> false
 * 2. true -> true
 * 3. false -> false
 */
public class KafkaZoneValidator implements ConfigValidator<ServiceSpec> {
    static final String KAFKA_POD_TYPE = "kafka";

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        return ZoneValidator.validate(oldConfig, newConfig, KAFKA_POD_TYPE);
    }
}
