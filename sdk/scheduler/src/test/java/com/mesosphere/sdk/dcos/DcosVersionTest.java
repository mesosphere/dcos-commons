package com.mesosphere.sdk.dcos;

import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the DcosVersion class.
 */
public class DcosVersionTest {
    private String testVersion = "test-version";

    @Test
    public void testConstruction() {
        DcosVersion dcosVersion = new DcosVersion(testVersion);
        Assert.assertEquals(testVersion, dcosVersion.getVersion());
    }
}
