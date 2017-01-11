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
        Capabilities capabilities = testCapabilities("0.9.0");
        Assert.assertFalse(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void test_170() throws IOException {
        Capabilities capabilities = testCapabilities("1.7.0");
        Assert.assertFalse(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void test_17dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.7-dev");
        Assert.assertFalse(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void test_180() throws IOException {
        Capabilities capabilities = testCapabilities("1.8.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void test_18dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.8-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertFalse(capabilities.supportsRLimits());
    }

    @Test
    public void test_190() throws IOException {
        Capabilities capabilities = testCapabilities("1.9.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void test_19dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.9-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void test_1100() throws IOException {
        Capabilities capabilities = testCapabilities("1.10.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void test_110dev() throws IOException {
        Capabilities capabilities = testCapabilities("1.10-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void test_200() throws IOException {
        Capabilities capabilities = testCapabilities("2.0.0");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    @Test
    public void test_20dev() throws IOException {
        Capabilities capabilities = testCapabilities("2.0-dev");
        Assert.assertTrue(capabilities.supportsNamedVips());
        Assert.assertTrue(capabilities.supportsRLimits());
    }

    private Capabilities testCapabilities(String version) throws IOException {
        when(mockDcosVersion.getElements()).thenReturn(new DcosVersion.Elements(version));
        return new Capabilities(mockDcosCluster);
    }
}
