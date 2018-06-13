package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * This validator enforces that the specified service name (e.g. /foo/bar/service) does not exceed 63 characters with
 * slashes removed. This is to ensure the DNS subdomain of the service does not exceed the character limit specified
 * in https://www.ietf.org/rfc/rfc1035.txt.
 *
 * The validator is only enforced on _new_ deployments and not on service upgrades.
 */
public class ServiceNameCannotBreakDNS implements ConfigValidator<ServiceSpec> {

    private static final Logger LOGGER = LoggingUtils.getLogger(ServiceNameCannotBreakDNS.class);

    private static final int MAX_SERVICE_NAME_LENGTH = 63;

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        // Is there a prior config?
        if (oldConfig.isPresent()) {
            // There is. We can at most WARN that the name is going to cause truncated DNS.
            if (!nameWithinSubDomainLimit(newConfig.getName())) {
                LOGGER.warn("The service name (without slashes) exceeds the maximum size allowed in a DNS subdomain.");
            }

            return Collections.emptyList();
        } else {
            // This is a new deployment, we can fail it if the service name is too large.
            if (!nameWithinSubDomainLimit(newConfig.getName())) {
                return Collections.singletonList(ConfigValidationError.valueError(
                        "service.name",
                        newConfig.getName(),
                        "Service name (without slashes) exceeds 63 characters. In order for service DNS " +
                                "to work correctly, the service name (without slashes) must not exceed 63 characters"));
            }

            return Collections.emptyList();
        }
    }

    private boolean nameWithinSubDomainLimit(String name) {
        // Each subdomain of a DNS address must be less than 63 characters.
        return EndpointUtils.removeSlashes(name).length() <= MAX_SERVICE_NAME_LENGTH;
    }
}
