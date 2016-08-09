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
    public void testHasNamedVipsSucceeds_1_8() throws URISyntaxException, IOException {
        mockDcosCluster =  MockDcosCluster.create("1.8.0");
        dcosCluster = mockDcosCluster.getDcosCluster();
        Capabilities capabilities = new Capabilities(dcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_9() throws URISyntaxException, IOException {
        mockDcosCluster =  MockDcosCluster.create("1.9.0");
        dcosCluster = mockDcosCluster.getDcosCluster();
        Capabilities capabilities = new Capabilities(dcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_2_0() throws URISyntaxException, IOException {
        mockDcosCluster =  MockDcosCluster.create("2.0.0");
        dcosCluster = mockDcosCluster.getDcosCluster();
        Capabilities capabilities = new Capabilities(dcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }
}
