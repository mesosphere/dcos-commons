package org.apache.mesos.dcos;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * This class tests the DcosCluster class.
 */
public class DcosClusterTest {
    private MockDcosCluster mockDcosCluster;
    private static final String TEST_VERSION = "1.8-dev";

    @Before
    public void beforeEach() throws URISyntaxException {
        mockDcosCluster = null;
    }

    @After
    public void afterEach() {
        if (mockDcosCluster != null) {
            mockDcosCluster.stop();
        }
        mockDcosCluster = null;
    }

    @Test
    public void testGetImageInfo() throws IOException, URISyntaxException {
        DcosCluster mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, TEST_VERSION)));
        Optional<DcosVersion> dcosVersion = mockDcosCluster.getDcosVersion();
        Assert.assertTrue(dcosVersion.isPresent());
        Assert.assertEquals(MockDcosCluster.TEST_BOOTSTRAP_ID, dcosVersion.get().getBootstrapId());
        Assert.assertEquals(MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, dcosVersion.get().getDcosImageCommit());
    }

    @Test
    public void testGetVersion() throws IOException, URISyntaxException {
        DcosCluster mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, TEST_VERSION)));
        Optional<DcosVersion> dcosVersion = mockDcosCluster.getDcosVersion();
        Assert.assertTrue(dcosVersion.isPresent());
        Assert.assertEquals(TEST_VERSION, dcosVersion.get().getVersion());
        Assert.assertEquals(1, dcosVersion.get().getElements().getFirstElement());
        Assert.assertEquals(8, dcosVersion.get().getElements().getSecondElement());
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionInt() throws IOException, URISyntaxException {
        DcosCluster mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "5")));
        Optional<DcosVersion> dcosVersion = mockDcosCluster.getDcosVersion();
        Assert.assertTrue(dcosVersion.isPresent());
        Assert.assertEquals("5", dcosVersion.get().getVersion());
        Assert.assertEquals(5, dcosVersion.get().getElements().getFirstElement());
        dcosVersion.get().getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionIntDot() throws IOException, URISyntaxException {
        DcosCluster mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "0.")));
        Optional<DcosVersion> dcosVersion = mockDcosCluster.getDcosVersion();
        Assert.assertTrue(dcosVersion.isPresent());
        Assert.assertEquals("0.", dcosVersion.get().getVersion());
        Assert.assertEquals(0, dcosVersion.get().getElements().getFirstElement());
        dcosVersion.get().getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionDot() throws IOException, URISyntaxException {
        DcosCluster mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, ".")));
        Optional<DcosVersion> dcosVersion = mockDcosCluster.getDcosVersion();
        Assert.assertTrue(dcosVersion.isPresent());
        Assert.assertEquals(".", dcosVersion.get().getVersion());
        dcosVersion.get().getElements().getFirstElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionString() throws IOException, URISyntaxException {
        DcosCluster mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "0.hello")));
        Optional<DcosVersion> dcosVersion = mockDcosCluster.getDcosVersion();
        Assert.assertTrue(dcosVersion.isPresent());
        Assert.assertEquals("0.hello", dcosVersion.get().getVersion());
        Assert.assertEquals(0, dcosVersion.get().getElements().getFirstElement());
        dcosVersion.get().getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionSuffix() throws IOException, URISyntaxException {
        DcosCluster mockDcosCluster = Mockito.mock(DcosCluster.class);
        Mockito.when(mockDcosCluster.getDcosVersion()).thenReturn(Optional.of(
                new DcosVersion(MockDcosCluster.TEST_BOOTSTRAP_ID, MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, "0.5-hey")));
        Optional<DcosVersion> dcosVersion = mockDcosCluster.getDcosVersion();
        Assert.assertTrue(dcosVersion.isPresent());
        Assert.assertEquals("0.5-hey", dcosVersion.get().getVersion());
        Assert.assertEquals(0, dcosVersion.get().getElements().getFirstElement());
        dcosVersion.get().getElements().getSecondElement();
    }

    @Test
    public void testGetUri() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create(TEST_VERSION);
        Assert.assertEquals(
                mockDcosCluster.getUri(), mockDcosCluster.getDcosCluster().getDcosUri());
    }
}
