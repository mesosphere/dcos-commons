package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * A {@link TLSRequiresServiceAccount} checks whether the configuration contains provisioning of TLS artifacts
 * and whether the provided {@link SchedulerFlags} contains a service account.
 */
public class TLSRequiresServiceAccount implements ConfigValidator<ServiceSpec> {

    private final SchedulerFlags flags;

    public TLSRequiresServiceAccount(SchedulerFlags flags) {
        this.flags = flags;
    }

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        if (!TaskUtils.getTasksWithTLS(newConfig).isEmpty()) {
            try {
                if (StringUtils.isBlank(flags.getServiceAccountUid())) {
                    return getConfigValidationErrors();
                }
            } catch (Exception e) {
                return getConfigValidationErrors();
            }
        }

        return Collections.emptyList();
    }

    private Collection<ConfigValidationError> getConfigValidationErrors() {
        String errorMessage = "Scheduler is missing a service account that is required for " +
                "provisioning TLS artifacts. Please configure in order to continue.";
        return Arrays.asList(
                ConfigValidationError.valueError("transport-encryption", "", errorMessage)
        );
    }

}
