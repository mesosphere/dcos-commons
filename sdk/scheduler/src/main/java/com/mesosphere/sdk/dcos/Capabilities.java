package com.mesosphere.sdk.dcos;

import java.io.IOException;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a set of capabilities that may or may not be supported in a given version of DC/OS.
 */
public class Capabilities {
    private static final Logger LOGGER = LoggerFactory.getLogger(Capabilities.class);

    private DcosCluster dcosCluster;

    public Capabilities(DcosCluster dcosCluster) {
        this.dcosCluster = dcosCluster;
    }

    public boolean supportsNamedVips() throws IOException, URISyntaxException {
        DcosVersion.Elements versionElements = dcosCluster.getDcosVersion().getElements();
        try {
            // Named Vips are supported by DC/OS 1.8 upwards.
            if (versionElements.getFirstElement() == 1) {
                return versionElements.getSecondElement() >= 8;
            }
            return versionElements.getFirstElement() > 1;
        } catch (NumberFormatException ex) {
            // incorrect version string. Todo(joerg84): Consider throwing an exception here.
            LOGGER.warn("Unable to parse version string for Named Vip", ex);
            return false;
        }
    }
}
