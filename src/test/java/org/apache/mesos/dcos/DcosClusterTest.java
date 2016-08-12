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
    private DcosCluster dcosCluster;
    private static final String TEST_VERSION = "1.8-dev";

    @Before
    public void beforeEach() throws URISyntaxException {
        mockDcosCluster = MockDcosCluster.create(TEST_VERSION);
        dcosCluster = mockDcosCluster.getDcosCluster();
    }

    @After
    public void afterEach() {
        mockDcosCluster.stop();
    }

    @Test
    public void testGetVersion() throws IOException, URISyntaxException {
        DcosVersion dcosVersion = dcosCluster.getDcosVersion();
        Assert.assertNotNull(dcosVersion);
        Assert.assertEquals(MockDcosCluster.TEST_BOOTSTRAP_ID, dcosVersion.getBootstrapId());
        Assert.assertEquals(MockDcosCluster.TEST_DCOS_IMAGE_COMMIT, dcosVersion.getDcosImageCommit());
        Assert.assertEquals(TEST_VERSION, dcosVersion.getVersion());
        Assert.assertEquals(1, dcosVersion.getVersionFirstElement());
        Assert.assertEquals(8, dcosVersion.getVersionSecondElement());
    }

    @Test
    public void testGetUri() throws IOException, URISyntaxException {
        Assert.assertEquals(mockDcosCluster.getUri(), dcosCluster.getDcosUri());
    }
}
