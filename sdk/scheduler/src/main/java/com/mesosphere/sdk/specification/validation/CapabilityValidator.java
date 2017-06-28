package com.mesosphere.sdk.specification.validation;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.*;
import org.apache.commons.collections.MapUtils;

/**
 * This class contains logic for validating that a {@link ServiceSpec} only requires features supported by the DC/OS
 * cluster being run on.
 */
public class CapabilityValidator {
    public void validate(ServiceSpec serviceSpec) throws CapabilityValidationException {
        validateGpuPolicy(serviceSpec);
        validateResourceRefinement(serviceSpec);
        for (PodSpec podSpec : serviceSpec.getPods()) {
            validate(podSpec);
        }
    }

    private void validateGpuPolicy(ServiceSpec serviceSpec) throws CapabilityValidationException {
        if (DefaultService.serviceSpecRequestsGpuResources(serviceSpec)
                && !Capabilities.getInstance().supportsGpuResource()) {
            throw new CapabilityValidationException(
                    "This cluster's DC/OS version does not support setting GPU_RESOURCE");
        }
    }

    private void validateResourceRefinement(ServiceSpec serviceSpec) throws CapabilityValidationException {
        // All pre-reserved-roles should be "*" if the capability is not supported
        if (!Capabilities.getInstance().supportsPreReservedResources()) {
            boolean hasPreReservedRoles = serviceSpec.getPods().stream()
                    .map(podSpec -> podSpec.getPreReservedRole())
                    .anyMatch(preReservedRole -> !preReservedRole.equals(Constants.ANY_ROLE));

            if (hasPreReservedRoles) {
                throw new CapabilityValidationException(
                        "This cluster's DC/OS version does not support consuming pre-reserved resources.");
            }
        }
    }

    private void validate(PodSpec podSpec) throws CapabilityValidationException {
        if (!podSpec.getRLimits().isEmpty() && !Capabilities.getInstance().supportsRLimits()) {
            throw new CapabilityValidationException(
                    "This cluster's DC/OS version does not support setting rlimits");
        }

        // if we don't have a cluster that supports CNI port mapping make sure that we don't either (1) specify
        // any networks and/ (2) if we are make sure we didn't explicitly do any port mapping or that
        // any of the tasks ask for ports (which will automatically be forwarded, this requiring port mapping).
        if (!podSpec.getNetworks().isEmpty()) {
            String cniNotSupportedMessage = "This cluster's DC/OS version does not support CNI port mapping";
            for (NetworkSpec networkSpec : podSpec.getNetworks()) {
                if (MapUtils.isNotEmpty(networkSpec.getPortMappings()) &&
                        !Capabilities.getInstance().supportsCNINetworking()) {
                    // ask for port mapping but not supported
                    throw new CapabilityValidationException(cniNotSupportedMessage);
                }
            }

            for (TaskSpec taskSpec : podSpec.getTasks()) {
                for (ResourceSpec resourceSpec : taskSpec.getResourceSet().getResources()) {
                    if (resourceSpec.getName().equals("ports") &&
                            !Capabilities.getInstance().supportsCNINetworking()) {
                        throw new CapabilityValidationException(cniNotSupportedMessage);
                    }
                }
            }
        }

        if (!podSpec.getSecrets().isEmpty()) {
            // TODO(MB) : Change validator if we decide to support DCOS_DIRECTIVE label
            // envBasedSecretSupportMessage =
            //        "This cluster's DC/OS version does not support environment-based Secrets";

            for (SecretSpec secretSpec : podSpec.getSecrets()) {
                if (secretSpec.getEnvKey().isPresent() &&
                        !Capabilities.getInstance().supportsEnvBasedSecretsProtobuf()) {
                    throw new CapabilityValidationException(
                            "This service does not support Secrets for current cluster's DC/OS version");
                }
                // Default is file-based Secret
                if ((secretSpec.getFilePath().isPresent() || !secretSpec.getEnvKey().isPresent())
                        && !Capabilities.getInstance().supportsFileBasedSecrets()) {
                    throw new CapabilityValidationException(
                            "This cluster's DC/OS version does not support file-based Secrets");
                }

            }
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
