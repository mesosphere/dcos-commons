package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * Validates that the taskSpecs only contain features supported by the DC/OS cluster being run on.
 */
public class TaskSpecsCannotUseUnsupportedFeatures implements ConfigValidator<ServiceSpec> {

  @Override
  public Collection<ConfigValidationError> validate(
          Optional<ServiceSpec> oldConfig,
          ServiceSpec newConfig)
  {
    Capabilities capabilities = Capabilities.getInstance();
    boolean supportsShm = capabilities.supportsShm();

    Collection<ConfigValidationError> errors = new ArrayList<>();
    for (PodSpec podSpec : newConfig.getPods()) {
      for (TaskSpec taskSpec : podSpec.getTasks()) {
        if (!supportsShm && (taskSpec.getSharedMemory().isPresent() ||
            taskSpec.getSharedMemorySize().isPresent()))
        {
          errors.add(ConfigValidationError.valueError(
              "task:" + taskSpec.getName(),
              "shm",
              "This DC/OS cluster does not support shared memory"));
        }
      }
    }
    return errors;
  }
}
