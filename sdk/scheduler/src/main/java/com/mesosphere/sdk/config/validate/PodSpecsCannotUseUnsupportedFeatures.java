package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.apache.commons.collections.MapUtils;

/**
 * This class contains logic for validating that a {@link ServiceSpec} only requires features supported by the DC/OS
 * cluster being run on.
 */
public class PodSpecsCannotUseUnsupportedFeatures implements ConfigValidator<ServiceSpec> {

    @Override
    public Collection<ConfigValidationError> validate(Optional<ServiceSpec> oldConfig, ServiceSpec newConfig) {
        Collection<ConfigValidationError> errors = new ArrayList<>();

        Capabilities capabilities = Capabilities.getInstance();
        boolean supportsPreReservedResources = capabilities.supportsPreReservedResources();
        boolean supportsGpus = capabilities.supportsGpuResource();
        boolean supportsRLimits = capabilities.supportsRLimits();
        boolean supportsCNI = capabilities.supportsCNINetworking();
        boolean supportsEnvBasedSecrets = capabilities.supportsEnvBasedSecretsProtobuf();
        boolean supportsFileBasedSecrets = capabilities.supportsFileBasedSecrets();

        for (PodSpec podSpec : newConfig.getPods()) {
            if (!supportsGpus && podRequestsGpuResources(podSpec)) {
                errors.add(ConfigValidationError.valueError("pod:" + podSpec.getType(), "gpus",
                        "This DC/OS cluster does not support GPU resources"));
            }

            if (!supportsPreReservedResources && !podSpec.getPreReservedRole().equals(Constants.ANY_ROLE)) {
                errors.add(ConfigValidationError.valueError("pod:" + podSpec.getType(), "pre-reserved-role",
                        "This DC/OS cluster does not support consuming pre-reserved resources."));
            }

            if (!supportsRLimits && !podSpec.getRLimits().isEmpty()) {
                errors.add(ConfigValidationError.valueError("pod:" + podSpec.getType(), "rlimits",
                        "This DC/OS cluster does not support setting rlimits"));
            }

            if (!supportsCNI && podRequestsCNI(podSpec)) {
                errors.add(ConfigValidationError.valueError("pod:" + podSpec.getType(), "network",
                        "This DC/OS cluster does not support CNI port mapping"));
            }

            // TODO(MB) : Change validator if we decide to support DCOS_DIRECTIVE label
            if (!supportsEnvBasedSecrets && podRequestsEnvBasedSecrets(podSpec)) {
                errors.add(ConfigValidationError.valueError("pod:" + podSpec.getType(), "secrets:env",
                        "This DC/OS cluster does not support environment-based secrets"));
            }
            if (!supportsFileBasedSecrets && podRequestsFileBasedSecrets(podSpec)) {
                errors.add(ConfigValidationError.valueError("pod:" + podSpec.getType(), "secrets:file",
                        "This DC/OS cluster does not support file-based secrets"));
            }
        }
        return errors;
    }

    public static boolean serviceRequestsGpuResources(ServiceSpec serviceSpec) {
        for (PodSpec podSpec : serviceSpec.getPods()) {
            if (podRequestsGpuResources(podSpec)) {
                return true;
            }
        }
        return false;
    }

    private static boolean podRequestsGpuResources(PodSpec podSpec) {
        // control automatic opt-in to scarce resources (GPUs) here. If the framework specifies GPU resources >= 1
        // then we opt-in to scarce resource, otherwise follow the default policy (which as of 8/3/17 was to opt-out)
        if (DcosConstants.DEFAULT_GPU_POLICY) {
            return true;
        }
        return podSpec.getTasks().stream()
                .flatMap(taskSpec -> taskSpec.getResourceSet().getResources().stream())
                .anyMatch(resourceSpec -> resourceSpec.getName().equals("gpus")
                        && resourceSpec.getValue().getScalar().getValue() >= 1);
    }

    private static boolean podRequestsCNI(PodSpec podSpec) {
        // if we don't have a cluster that supports CNI port mapping make sure that we don't either (1) specify
        // any networks and/ (2) if we are make sure we didn't explicitly do any port mapping or that
        // any of the tasks ask for ports (which will automatically be forwarded, this requiring port mapping).
        for (NetworkSpec networkSpec : podSpec.getNetworks()) {
            if (!MapUtils.isEmpty(networkSpec.getPortMappings())) {
                return true;
            }
        }

        for (TaskSpec taskSpec : podSpec.getTasks()) {
            for (ResourceSpec resourceSpec : taskSpec.getResourceSet().getResources()) {
                if (resourceSpec.getName().equals("ports")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean podRequestsEnvBasedSecrets(PodSpec podSpec) {
        for (SecretSpec secretSpec : podSpec.getSecrets()) {
            if (secretSpec.getEnvKey().isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean podRequestsFileBasedSecrets(PodSpec podSpec) {
        for (SecretSpec secretSpec : podSpec.getSecrets()) {
            // Default is file-based Secret
            if (secretSpec.getFilePath().isPresent() || !secretSpec.getEnvKey().isPresent()) {
                return true;
            }
        }
        return false;
    }
}
