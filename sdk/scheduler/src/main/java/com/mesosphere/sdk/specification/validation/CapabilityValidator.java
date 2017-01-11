package com.mesosphere.sdk.specification.validation;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.specification.ContainerSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.io.IOException;


/**
 * This class contains logic for validating that a {@link ServiceSpec} only requires features supported by the DC/OS
 * cluster being run on.
 */
public class CapabilityValidator {
    private final Capabilities capabilities;

    public CapabilityValidator(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public void validate(ServiceSpec serviceSpec) throws CapabilityValidationException {
        for (PodSpec podSpec : serviceSpec.getPods()) {
            validate(podSpec);
        }
    }

    private void validate(PodSpec podSpec) throws CapabilityValidationException {
        if (podSpec.getContainer().isPresent()) {
            validate(podSpec.getContainer().get());
        }
    }

    private void validate(ContainerSpec containerSpec) throws CapabilityValidationException {
        try {
            if (!capabilities.supportsRLimits() && !containerSpec.getRLimits().isEmpty()) {
                throw new CapabilityValidationException(
                        "This cluster's DC/OS version does not support setting rlimits");
            }
        } catch (IOException e) {
            throw new CapabilityValidationException("Failed to determine capabilities: " + e.getMessage());
        }
    }

    /**
     * Exception thrown when a {@link ServiceSpec} requires DC/OS features not supported by the active cluster.
     */
    public static class CapabilityValidationException extends Exception {
        public CapabilityValidationException(Throwable ex) {
            super(ex);
        }

        public CapabilityValidationException(String msg) {
            super(msg);
        }

        public CapabilityValidationException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
