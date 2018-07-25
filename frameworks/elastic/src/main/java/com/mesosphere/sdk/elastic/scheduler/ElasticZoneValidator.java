package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.config.validate.ZoneValidator;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.Collection;
import java.util.Optional;

/**
 * This class validates that referencing Zones in placement constraints can only support the following transitions.
 *
 * 1. null  --> false
 * 2. true  --> true
 * 3. false --> false
 */
public class ElasticZoneValidator implements ConfigValidator<ServiceSpec> {
    static final String MASTER_POD_TYPE = "master";
    static final String DATA_POD_TYPE = "data";
    static final String INGEST_POD_TYPE = "ingest";
    static final String COORDINATOR_POD_TYPE = "coordinator";

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        return ZoneValidator.validate(
                oldConfig,
                newConfig,
                MASTER_POD_TYPE,
                DATA_POD_TYPE,
                INGEST_POD_TYPE,
                COORDINATOR_POD_TYPE);
    }
}
