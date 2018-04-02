package com.mesosphere.sdk.dcos;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the DcosVersion class.
 */
public class DcosVersionTest {
    private String testVersion = "test-version";
    private String dcosVariantKey = "dcos-variant";
    private String dcosVersionKey = "version";

    @Test
    public void testConstruction() {
        DcosVersion dcosVersion = new DcosVersion(testVersion, DcosVersion.DcosVariant.UNKNOWN);
        Assert.assertEquals(testVersion, dcosVersion.getVersion());
    }

    @Test
    public void testJsonContruction() {
        JSONObject j = new JSONObject();
        j.put(dcosVersionKey, testVersion);
        Assert.assertEquals(DcosVersion.DcosVariant.UNKNOWN, new DcosVersion(j).getDcosVariant());

        Assert.assertEquals(
                DcosVersion.DcosVariant.OPEN,
                new DcosVersion(
                        j.put(dcosVariantKey, DcosVersion.DcosVariant.OPEN.toString())
                ).getDcosVariant()
        );
        j.remove(dcosVariantKey);

        Assert.assertEquals(
                DcosVersion.DcosVariant.ENTERPRISE,
                new DcosVersion(
                        j.put(dcosVariantKey, DcosVersion.DcosVariant.ENTERPRISE.toString())
                ).getDcosVariant()
        );
        j.remove(dcosVariantKey);

        Assert.assertEquals(
                DcosVersion.DcosVariant.UNKNOWN,
                new DcosVersion(
                        j.put(dcosVariantKey, DcosVersion.DcosVariant.UNKNOWN.toString())
                ).getDcosVariant()
        );
        j.remove(dcosVariantKey);

        Assert.assertEquals(
                DcosVersion.DcosVariant.UNKNOWN,
                new DcosVersion(
                        j.put(dcosVariantKey, "DC/OS Enterprise")
                ).getDcosVariant()
        );
    }
}
