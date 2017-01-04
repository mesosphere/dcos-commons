package com.mesosphere.sdk.dcos;

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

    @After
    public void afterEach() {
        if (mockDcosCluster != null) {
            mockDcosCluster.stop();
        }
    }

    @Test
    public void testHasNamedVipsFails_0_9() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("0.9.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsFails_1_7() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.7.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsFails_1_7dev() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.7-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_8() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.8.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_8dev() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.8-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_9() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.9.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_9dev() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.9-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_2_0() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("2.0.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_2_0dev() throws URISyntaxException, IOException {
        mockDcosCluster =  MockDcosCluster.create("2.0-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasRLimitsFails_0_9() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("0.9.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsFails_1_7() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.7.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsFails_1_7dev() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.7-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsFails_1_8() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.8.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsFails_1_8dev() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.8-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsSucceeds_1_9() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.9.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsSucceeds_1_9dev() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("1.9-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsSucceeds_2_0() throws URISyntaxException, IOException {
        mockDcosCluster = MockDcosCluster.create("2.0.0");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void testHasRLimitsSucceeds_2_0dev() throws URISyntaxException, IOException {
        mockDcosCluster =  MockDcosCluster.create("2.0-dev");
        Capabilities capabilities = new Capabilities(mockDcosCluster.getDcosCluster());
        Assert.assertTrue(capabilities.supportsRLimits());
    }
}
