package org.apache.mesos.dcos;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testng.Assert;

import java.io.IOException;
import java.net.URISyntaxException;

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
        mockDcosCluster = MockDcosCluster.create(TEST_VERSION);
        DcosVersion dcosVersion = mockDcosCluster.getDcosCluster().getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals(MockDcosCluster.TEST_BOOTSTRAP_ID, dcosVersion.getBootstrapId());
        Assert.assertEquals(MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, dcosVersion.getDcosImageCommit());
    }

    @Test
    public void testGetVersion() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create(TEST_VERSION);
        DcosVersion dcosVersion = mockDcosCluster.getDcosCluster().getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals(TEST_VERSION, dcosVersion.getVersion());
        Assert.assertEquals(1, dcosVersion.getElements().getFirstElement());
        Assert.assertEquals(8, dcosVersion.getElements().getSecondElement());
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionInt() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create("5");
        DcosVersion dcosVersion = mockDcosCluster.getDcosCluster().getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals("5", dcosVersion.getVersion());
        Assert.assertEquals(5, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionIntDot() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create("0.");
        DcosVersion dcosVersion = mockDcosCluster.getDcosCluster().getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals("0.", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionDot() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create(".");
        DcosVersion dcosVersion = mockDcosCluster.getDcosCluster().getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals(".", dcosVersion.getVersion());
        dcosVersion.getElements().getFirstElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionString() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create("0.hello");
        DcosVersion dcosVersion = mockDcosCluster.getDcosCluster().getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals("0.hello", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionSuffix() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create("0.5-hey");
        DcosVersion dcosVersion = mockDcosCluster.getDcosCluster().getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals("0.5-hey", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test
    public void testGetUri() throws IOException, URISyntaxException {
        mockDcosCluster = MockDcosCluster.create(TEST_VERSION);
        Assert.assertEquals(
                mockDcosCluster.getUri(), mockDcosCluster.getDcosCluster().getDcosUri());
    }
}
