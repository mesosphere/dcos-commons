package com.mesosphere.sdk.dcos.clients;

import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.DcosVersion;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Tests for the {@link DcosVersionClient} class.
 */
public class DcosClusterClientTest {

    private static final String EXPECTED_URI = DcosConstants.MESOS_LEADER_URI + DcosVersionClient.DCOS_VERSION_PATH;
    private static final String TEST_VERSION = "1.9-dev";
    public static final String TEST_BOOTSTRAP_ID = "test-bootstrap-id";
    public static final String TEST_DCOS_IMAGE_COMMIT = "test-dcos-image-commit";
    private static final String RESPONSE_TEMPLATE =
            "{ 'version': '%s', " +
            "'dcos-image-commit': '" + TEST_DCOS_IMAGE_COMMIT + "', " +
            "'bootstrap-id': '" + TEST_BOOTSTRAP_ID + "' }";

    private static class TestDcosCluster extends DcosVersionClient {

        private final String version;

        private TestDcosCluster(String version) {
            super();
            this.version = version;
        }

        @Override
        protected String fetchUri(String path) throws IOException {
            if (!path.equals(EXPECTED_URI)) {
                throw new IOException(String.format("Expected URI '%s', got '%s'", EXPECTED_URI, path));
            }
            return String.format(RESPONSE_TEMPLATE, version);
        }
    }

    @Test
    public void testGetVersion() throws IOException, URISyntaxException {
        DcosVersion dcosVersion = new TestDcosCluster(TEST_VERSION).getDcosVersion();
        Assert.assertEquals(TEST_VERSION, dcosVersion.getVersion());
        Assert.assertEquals(1, dcosVersion.getElements().getFirstElement());
        Assert.assertEquals(9, dcosVersion.getElements().getSecondElement());
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionInt() throws IOException, URISyntaxException {
        DcosVersion dcosVersion = new TestDcosCluster("5").getDcosVersion();
        Assert.assertEquals("5", dcosVersion.getVersion());
        Assert.assertEquals(5, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionIntDot() throws IOException, URISyntaxException {
        DcosVersion dcosVersion = new TestDcosCluster("0.").getDcosVersion();
        Assert.assertEquals("0.", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionDot() throws IOException, URISyntaxException {
        DcosVersion dcosVersion = new TestDcosCluster(".").getDcosVersion();
        Assert.assertEquals(".", dcosVersion.getVersion());
        dcosVersion.getElements().getFirstElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionString() throws IOException, URISyntaxException {
        DcosVersion dcosVersion = new TestDcosCluster("0.hello").getDcosVersion();
        Assert.assertEquals("0.hello", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test(expected = NumberFormatException.class)
    public void testGetBadVersionSuffix() throws IOException, URISyntaxException {
        DcosVersion dcosVersion = new TestDcosCluster("0.5-hey").getDcosVersion();
        Assert.assertEquals("0.5-hey", dcosVersion.getVersion());
        Assert.assertEquals(0, dcosVersion.getElements().getFirstElement());
        dcosVersion.getElements().getSecondElement();
    }

    @Test
    public void testGetUri() throws IOException, URISyntaxException {
        Assert.assertEquals(DcosConstants.MESOS_LEADER_URI, new TestDcosCluster("foo").getDcosUri().toString());
    }
}
