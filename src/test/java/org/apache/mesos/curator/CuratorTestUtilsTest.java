package org.apache.mesos.curator;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link CuratorUtils}
 */
public class CuratorTestUtilsTest {

    @Test
    public void testServiceRootPath() {
        assertEquals("/dcos-service-test", CuratorUtils.toServiceRootPath("/test"));
        assertEquals("/dcos-service-test", CuratorUtils.toServiceRootPath("test"));
        assertEquals("/dcos-service-/test", CuratorUtils.toServiceRootPath("//test"));
    }

    @Test
    public void testJoinPath() {
        assertEquals("test/path", CuratorUtils.join("test", "path"));
        assertEquals("test/path", CuratorUtils.join("test", "/path"));
        assertEquals("test/path", CuratorUtils.join("test/", "path"));
        assertEquals("test/path", CuratorUtils.join("test/", "/path"));
        assertEquals("test//path", CuratorUtils.join("test//", "/path"));
        assertEquals("test//path", CuratorUtils.join("test/", "//path"));
    }

    @Test
    public void testSerialize() {
        final UUID uuid = UUID.randomUUID();
        assertEquals("hello", CuratorUtils.deserialize(CuratorUtils.serialize("hello")));
        assertEquals("\0", CuratorUtils.deserialize(CuratorUtils.serialize("\0")));
        assertEquals("hi\nhey", CuratorUtils.deserialize(CuratorUtils.serialize("hi\nhey")));
        assertEquals(uuid.toString(), CuratorUtils.deserialize(CuratorUtils.serialize(uuid)));
    }
}
