package com.mesosphere.sdk.dcos;

import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the DcosVersion class.
 */
public class DcosVersionTest {
    private String testBootstrapId = "test-bootstrap-id";
    private String testDcosImageCommit = "test-dcos-image-commit";
    private String testVersion = "test-version";

    @Test
    public void testConstruction() {
        DcosVersion dcosVersion = new DcosVersion(
                testBootstrapId,
                testDcosImageCommit,
                testVersion);

        Assert.assertEquals(testBootstrapId, dcosVersion.getBootstrapId());
        Assert.assertEquals(testDcosImageCommit, dcosVersion.getDcosImageCommit());
        Assert.assertEquals(testVersion, dcosVersion.getVersion());
    }
}
