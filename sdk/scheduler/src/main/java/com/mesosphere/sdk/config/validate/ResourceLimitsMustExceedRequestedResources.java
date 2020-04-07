package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceLimits;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * This class contains logic for validating that a {@link ServiceSpec} only requires features
 * supported by the DC/OS cluster being run on.
 */
public class ResourceLimitsMustExceedRequestedResources implements ConfigValidator<ServiceSpec> {
  private double getScalarResources(ResourceSet resourceSet, String resourceName) {
    return resourceSet.getResources()
            .stream()
            .filter((r) -> r.getName().equals(resourceName) && r.getValue().hasScalar())
            .map(r -> r.getValue().getScalar().getValue())
            .findFirst()
            .orElseGet(() -> 0d);

  }

  private static final ConfigValidationError formatError(String resourceType, String taskName, Double request, Double limit, ResourceLimits resourceLimits) {
    DecimalFormat df = new DecimalFormat("#.###");
    String errMsg = String.format(
            "The %s resource-limits for task '%s', %s, is less than the requested amount, %s",
            resourceType,
            taskName,
            df.format(limit),
            df.format(request));
    return ConfigValidationError.valueError(
            "ResourceLimits",
            resourceLimits.toString(),
            errMsg);
  }

  //SUPPRESS CHECKSTYLE CyclomaticComplexity
  @Override
  public Collection<ConfigValidationError> validate(
      Optional<ServiceSpec> oldConfig,
      ServiceSpec newConfig) {
    Collection<ConfigValidationError> errors = new ArrayList<>();

    for (PodSpec podSpec: newConfig.getPods()) {
      for (TaskSpec taskSpec : podSpec.getTasks()) {
        ResourceLimits resourceLimits = taskSpec.getResourceSet().getResourceLimits();

        resourceLimits.getCpusDouble().ifPresent((cpusLimit) -> {
          double cpusRequest = getScalarResources(taskSpec.getResourceSet(), Constants.CPUS_RESOURCE_TYPE);
          if (cpusLimit < cpusRequest) {
            errors.add(formatError(Constants.CPUS_RESOURCE_TYPE, taskSpec.getName(), cpusRequest, cpusLimit, resourceLimits));
          }
        });
        resourceLimits.getMemoryDouble().ifPresent((memLimit) -> {
          double memRequest = getScalarResources(taskSpec.getResourceSet(), Constants.MEMORY_RESOURCE_TYPE);
          if (memLimit < memRequest) {
            errors.add(formatError(Constants.MEMORY_RESOURCE_TYPE, taskSpec.getName(), memRequest, memLimit, resourceLimits));
          }
        });
      }
    }
    return errors;
  }
}
