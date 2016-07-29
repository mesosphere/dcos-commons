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
        return dcosCluster.getDcosVersion().getVersion().startsWith("1.8");
    }
}
