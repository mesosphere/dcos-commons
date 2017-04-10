package com.mesosphere.sdk.dcos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This class represents a set of capabilities that may or may not be supported in a given version of DC/OS.
 */
public class Capabilities {
    private static final Logger LOGGER = LoggerFactory.getLogger(Capabilities.class);

    private DcosCluster dcosCluster;

    public Capabilities(DcosCluster dcosCluster) {
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

    public boolean supportCniPortMapping() throws IOException {
        // CNI port-mapping is supported by DC/OS 1.10 upwards
        return hasOrExceedsVersion(1, 10);
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
