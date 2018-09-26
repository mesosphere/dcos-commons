package com.mesosphere.sdk.dcos;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.clients.DcosVersionClient;
import com.mesosphere.sdk.offer.LoggingUtils;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * This class represents a set of capabilities that may or may not be supported in a given version of DC/OS.
 */
public class Capabilities {
    private static final Logger LOGGER = LoggingUtils.getLogger(Capabilities.class);
    private static final Object lock = new Object();
    private static Capabilities capabilities;

    private final DcosVersion dcosVersion;

    public static Capabilities getInstance() {
        synchronized (lock) {
            if (capabilities == null) {
                try {
                    DcosVersionClient client = new DcosVersionClient(new DcosHttpExecutor(new DcosHttpClientBuilder()));
                    capabilities = new Capabilities(client.getDcosVersion());
                } catch (IOException e) {
                    LOGGER.error("Unable to fetch DC/OS version.", e);
                    throw new IllegalStateException(e);
                }
            }

            return capabilities;
        }
    }

    /**
     * Overrides the cluster capabilities object returned by {@link #getInstance()}, for use in tests.
     */
    public static void overrideCapabilities(Capabilities overrides) {
        synchronized (lock) {
            capabilities = overrides;
        }
    }

    @VisibleForTesting
    public Capabilities(DcosVersion dcosVersion) {
        this.dcosVersion = dcosVersion;
    }

    public DcosVersion getDcosVersion() {
        return dcosVersion;
    }

    public boolean supportsNamedVips() {
        // Named Vips are supported by DC/OS 1.8 upwards.
        return hasOrExceedsVersion(1, 8);
    }

    public boolean supportsRLimits() {
        // Container rlimits are supported by DC/OS 1.9 upwards.
        return hasOrExceedsVersion(1, 9);
    }

    public boolean supportsGpuResource() {
        // GPU_RESOURCE is supported by DC/OS 1.9 upwards.
        return hasOrExceedsVersion(1, 9);
    }

    public boolean supportsFileBasedSecrets() {
        // File-based Secret is supported by DC/OS 1.10 upwards
        return hasOrExceedsVersion(1, 10);
    }

    public boolean supportsEnvBasedSecretsProtobuf() {
        // Environment-based Secret is supported by DC/OS 1.10 upwards
        return hasOrExceedsVersion(1, 10);
    }

    // We do not support DCOS_DIRECTIVE label for environment-based Secrets. We may in the future.
    public boolean supportsEnvBasedSecretsDirectiveLabel() {
        // DCOS_DIRECTIVE label for Secret environment is supported by DC/OS 1.8 upwards
        return hasOrExceedsVersion(1, 8);
    }

    public boolean supportsCNINetworking() {
        // CNI port-mapping is supported by DC/OS 1.9 upwards.
        return hasOrExceedsVersion(1, 9);
    }

    public boolean supportsPreReservedResources() {
        // The RESERVATION_REFINEMENT capability is supported by DC/OS 1.10 upwards.
        return hasOrExceedsVersion(1, 10);
    }

    public boolean supportsV1APIByDefault() {
        // The Mesos V1 HTTP API with strict mode enabled is supported by DC/OS 1.11 upwards
        return hasOrExceedsVersion(1, 11);
    }

    public boolean supportsDomains() {
        // A given DC/OS cluster may or may not have domain information available in Offers.  This information is
        // dependent upon the cluster operator and is unknown to the scheduler.  However domain information is only
        // present on DC/OS 1.11+ clusters.
        return hasOrExceedsVersion(1, 11);
    }

    public boolean supportsProfileMountVolumes() {
        // MOUNT volumes with profiles are supportedby DC/OS 1.12 upwards.
        return hasOrExceedsVersion(1, 12);
    }

    private boolean hasOrExceedsVersion(int major, int minor) {
        DcosVersion.Elements versionElements = dcosVersion.getElements();
        try {
            if (versionElements.getFirstElement() > major) {
                return true;
            } else if (versionElements.getFirstElement() == major) {
                return versionElements.getSecondElement() >= minor;
            }
            return false;
        } catch (NumberFormatException ex) {
            // incorrect version string.
            LOGGER.error("Unable to parse DC/OS version string: {}", dcosVersion.getVersion());
            throw new IllegalStateException(ex);
        }
    }
}
