package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.config.validate.ZoneValidator;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.util.Collection;
import java.util.Optional;

/**
 * This class validates that the DETECT_ZONES envvar can only support the following transitions.
 * <p>
 * 1. null -> false
 * 2. true -> true
 * 3. false -> false
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class HDFSZoneValidator implements ConfigValidator<ServiceSpec> {
  private static final String NAME_POD_TYPE = "name";

  private static final String JOURNAL_POD_TYPE = "journal";

  private static final String DATA_POD_TYPE = "data";

  @Override
  public Collection<ConfigValidationError> validate(
      Optional<ServiceSpec> oldConfig,
      ServiceSpec newConfig)
  {
    return ZoneValidator
        .validate(oldConfig, newConfig, NAME_POD_TYPE, JOURNAL_POD_TYPE, DATA_POD_TYPE);
  }
}
