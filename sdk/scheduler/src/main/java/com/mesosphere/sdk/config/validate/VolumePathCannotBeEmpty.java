package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Validates that volume path is not empty if new ServiceSpec has a volume
 */
public class VolumePathCannotBeEmpty implements ConfigValidator<ServiceSpec> {
    @Override
    public Collection<ConfigValidationError> validate(ServiceSpec nullableOldConfig, ServiceSpec newConfig) {
        List<ConfigValidationError> errors = new ArrayList<>();

        for (PodSpec podSpec : newConfig.getPods()) {
            for (TaskSpec taskSpec : podSpec.getTasks()) {
                Collection<VolumeSpec> volumeSpecs = taskSpec.getResourceSet().getVolumes();
                if (volumeSpecs.isEmpty()) {
                    continue;
                }
                for (VolumeSpec volumeSpec : volumeSpecs) {
                    if (StringUtils.isBlank(volumeSpec.getContainerPath())) {
                        errors.add(ConfigValidationError.valueError(
                                String.format("TaskVolumes[taskname:%s]", taskSpec.getName()),
                                volumeSpec.toString(),
                                "Volume path can not be empty."));
                    }
                }
            }
        }
        return errors;
    }
}
