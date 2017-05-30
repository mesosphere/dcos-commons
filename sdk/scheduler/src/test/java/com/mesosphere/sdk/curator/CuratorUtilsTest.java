package com.mesosphere.sdk.curator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link CuratorUtils}
 */
public class CuratorUtilsTest {

    @Test
    public void testServiceRootPath() {
        assertEquals("/dcos-service-test", CuratorUtils.getServiceRootPath("/test"));
        assertEquals("/dcos-service-test", CuratorUtils.getServiceRootPath("test"));
        assertEquals("/dcos-service-/test", CuratorUtils.getServiceRootPath("//test"));
    }
}
