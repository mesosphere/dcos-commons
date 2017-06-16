package com.mesosphere.sdk.dcos;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This class represents a set of capabilities that may or may not be supported in a given version of DC/OS.
 */
public class Capabilities {
    private static final Logger LOGGER = LoggerFactory.getLogger(Capabilities.class);
    private static final Object lock = new Object();
    private static Capabilities capabilities;
    DcosCluster dcosCluster;

    public static Capabilities getInstance() {
        synchronized (lock) {
            if (capabilities == null) {
                capabilities = new Capabilities(new DcosCluster());
            }

            return capabilities;
        }
    }

    public static void overrideCapabilities(Capabilities overrides) {
        synchronized (lock) {
            capabilities = overrides;
        }
    }

    @VisibleForTesting
    Capabilities(DcosCluster dcosCluster) {
        this.dcosCluster = dcosCluster;
    }

    public boolean supportsNamedVips() throws IOException {
        // Named Vips are supported by DC/OS 1.8 upwards.
        return hasOrExceedsVersion(1, 8);
    }

    public boolean supportsRLimits() throws IOException {
        // Container rlimits are supported by DC/OS 1.9 upwards.
        return hasOrExceedsVersion(1, 9);
    }

    public boolean supportsGpuResource() throws IOException {
        // GPU_RESOURCE is supported by DC/OS 1.9 upwards
        return hasOrExceedsVersion(1, 9);
    }

    public boolean supportsCNINetworking() throws IOException {
        // CNI port-mapping is supported by DC/OS 1.10 upwards
        return hasOrExceedsVersion(1, 9);
    }

    public boolean supportsPreReservedResources() {
        return false;
    }

    private boolean hasOrExceedsVersion(int major, int minor) throws IOException {
        DcosVersion.Elements versionElements = dcosCluster.getDcosVersion().getElements();
        try {
            if (versionElements.getFirstElement() > major) {
                return true;
            } else if (versionElements.getFirstElement() == major) {
                return versionElements.getSecondElement() >= minor;
            }
            return false;
        } catch (NumberFormatException ex) {
            // incorrect version string.
            LOGGER.error("Unable to parse DC/OS version string: {}", dcosCluster.getDcosVersion().getVersion());
            return false;
        }

    }
}
