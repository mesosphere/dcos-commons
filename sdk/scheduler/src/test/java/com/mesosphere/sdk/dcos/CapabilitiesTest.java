package com.mesosphere.sdk.dcos;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.mockito.Mockito.when;

/**
 * Tests for the {@link Capabilities} class
 */
public class CapabilitiesTest {

    @Mock private DcosCluster mockDcosCluster;
    @Mock private DcosVersion mockDcosVersion;

    @Before
    public void beforeEach() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(mockDcosCluster.getDcosVersion()).thenReturn(mockDcosVersion);
    }

    @Test
    public void test_090() throws IOException {
        testClusterVersion("0.9.0", false, false);
    }

    @Test
    public void test_170() throws IOException {
        testClusterVersion("1.7.0", false, false);
    }

    @Test
    public void test_17dev() throws IOException {
        testClusterVersion("1.7-dev", false, false);
    }

    @Test
    public void test_180() throws IOException {
        testClusterVersion("1.8.0", true, false);
    }

    @Test
    public void test_18dev() throws IOException {
        testClusterVersion("1.8-dev", true, false);
    }

    @Test
    public void test_190() throws IOException {
        testClusterVersion("1.9.0", true, true);
    }

    @Test
    public void test_19dev() throws IOException {
        testClusterVersion("1.9-dev", true, true);
    }

    @Test
    public void test_1100() throws IOException {
        testClusterVersion("1.10.0", true, true);
    }

    @Test
    public void test_110dev() throws IOException {
        testClusterVersion("1.10-dev", true, true);
    }

    @Test
    public void test_200() throws IOException {
        testClusterVersion("2.0.0", true, true);
    }

    @Test
    public void test_20dev() throws IOException {
        testClusterVersion("2.0-dev", true, true);
    }

    private void testClusterVersion(String version, boolean expectNamedVips, boolean expectRLimits) throws IOException {
        when(mockDcosVersion.getElements()).thenReturn(new DcosVersion.Elements(version));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertEquals(version + " named vips", expectNamedVips, capabilities.supportsNamedVips());
        Assert.assertEquals(version + " rlimits", expectRLimits, capabilities.supportsRLimits());
    }
}
