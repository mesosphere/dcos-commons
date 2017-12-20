package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.validation.ZoneValidator;

import java.util.*;

/**
 * This class validates that the DETECT_ZONES envvar can only support the following transitions.
 *
 * 1. null  --> false
 * 2. true  --> true
 * 3. false --> false
 */
public class CassandraZoneValidator implements ConfigValidator<ServiceSpec> {
    static final String POD_TYPE = "node";
    static final String TASK_NAME = "server";

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        return ZoneValidator.validate(oldConfig, newConfig, POD_TYPE, TASK_NAME);
    }
}
