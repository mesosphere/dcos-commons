package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.ZoneValidator;

import java.util.Collection;
import java.util.Optional;

/**
 * This class validates that the DETECT_ZONES envvar can only support the following transitions.j
 *
 * 1. null -> false
 * 2. true -> true
 * 3. false -> false
 */
public class HDFSZoneValidator implements ConfigValidator<ServiceSpec> {
    static final String POD_TYPE = "name";
    static final String TASK_NAME = "node";

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        return ZoneValidator.validate(oldConfig, newConfig, POD_TYPE, TASK_NAME);
    }
}
