package org.apache.mesos.dcos;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * This class tests the Capabilities class.
 */
public class CapabilitiesTest {
    private MockDcosCluster mockDcosCluster;
    private DcosCluster dcosCluster;

    @After
    public void afterEach() {
        if (mockDcosCluster != null) {
            mockDcosCluster.stop();
        }
    }

    @Test
    public void testHasNamedVipsFails() throws URISyntaxException, IOException {
        mockDcosCluster =  MockDcosCluster.create("1.7.0");
        dcosCluster = mockDcosCluster.getDcosCluster();
        Capabilities capabilities = new Capabilities(dcosCluster);
        Assert.assertFalse(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds() throws URISyntaxException, IOException {
        mockDcosCluster =  MockDcosCluster.create("1.8.0");
        dcosCluster = mockDcosCluster.getDcosCluster();
        Capabilities capabilities = new Capabilities(dcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }
}
