package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.ServiceSpec;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Collection;
import java.util.Optional;

/**
 * This class contains logic for validating that a {@link ServiceSpec} only requires features
 * supported by the DC/OS cluster being run on.
 */
public class ResourceLimitsMustExceedRequestedResources implements ConfigValidator<ServiceSpec> {

  //SUPPRESS CHECKSTYLE CyclomaticComplexity
  @Override
  public Collection<ConfigValidationError> validate(
      Optional<ServiceSpec> oldConfig,
      ServiceSpec newConfig)
  {
      throw new NotImplementedException();
  }
}
