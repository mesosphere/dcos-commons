package org.apache.mesos.dcos;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * This class tests the Capabilities class.
 */
public class CapabilitiesTest {
    private DcosCluster mockDcosCluster;

    @Test
    public void testHasNamedVipsFails_0_9() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "0.9.0")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertFalse(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsFails_1_7() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "1.7.0")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertFalse(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsFails_1_7dev() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "1.7-dev")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertFalse(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_8() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "1.8.0")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_8dev() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "1.8-dev")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_9() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "1.9.0")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_1_9dev() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "1.9-dev")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_2_0() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "2.0.0")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }

    @Test
    public void testHasNamedVipsSucceeds_2_0dev() throws URISyntaxException, IOException {
        mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "2.0-dev")));
        Capabilities capabilities = new Capabilities(mockDcosCluster);
        Assert.assertTrue(capabilities.supportsNamedVips());
    }
}
