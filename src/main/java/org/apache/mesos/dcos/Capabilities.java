package org.apache.mesos.dcos;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * This class represents a set of capabilities that may or may not be supported in a given version of DC/OS.
 */
public class Capabilities {
    private DcosCluster dcosCluster;

    public Capabilities(DcosCluster dcosCluster) {
        this.dcosCluster = dcosCluster;
    }

    public boolean supportsNamedVips() throws IOException, URISyntaxException {
        // Named Vips are supported by DC/OS 1.8 upwards.
        String[] version = dcosCluster.getDcosVersion().getVersion().split("\\.");

        if (version.length < 2) {
            // incorrect version string. Todo(joerg84): Consider throwing an exception here.
            return false;
        }

        try {
            if (Integer.parseInt(version[0]) >= 2 || Integer.parseInt(version[1]) >= 8) {
                return true;
            } else {
                return false;
            }
        } catch (NumberFormatException ex) {
            // incorrect version string. Todo(joerg84): Consider throwing an exception here.
            return false;
        }
    }
}
