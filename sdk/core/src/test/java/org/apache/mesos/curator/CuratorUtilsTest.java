package org.apache.mesos.curator;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link CuratorUtils}
 */
public class CuratorUtilsTest {

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

        assertEquals("/test/path", CuratorUtils.join("/test", "path"));
        assertEquals("/test/path", CuratorUtils.join("/test", "/path"));
        assertEquals("/test/path", CuratorUtils.join("/test/", "path"));
        assertEquals("/test/path", CuratorUtils.join("/test/", "/path"));
        assertEquals("/test//path", CuratorUtils.join("/test//", "/path"));
        assertEquals("/test//path", CuratorUtils.join("/test/", "//path"));
    }

    @Test
    public void testGetParentPaths() {
        assertEquals(new ArrayList<String>(), CuratorUtils.getParentPaths(""));
        assertEquals(new ArrayList<String>(), CuratorUtils.getParentPaths("test"));
        assertEquals(Arrays.asList("test"), CuratorUtils.getParentPaths("test/path"));
        assertEquals(Arrays.asList("test", "test/path"),
                CuratorUtils.getParentPaths("test/path/foo"));
        assertEquals(Arrays.asList("test", "test/path", "test/path/foo"),
                CuratorUtils.getParentPaths("test/path/foo/bar"));
        assertEquals(Arrays.asList("test", "test/path", "test/path/foo", "test/path/foo/bar"),
                CuratorUtils.getParentPaths("test/path/foo/bar/baz"));

        assertEquals(new ArrayList<String>(), CuratorUtils.getParentPaths("/"));
        assertEquals(new ArrayList<String>(), CuratorUtils.getParentPaths("/test"));
        assertEquals(Arrays.asList("/test"), CuratorUtils.getParentPaths("/test/path"));
        assertEquals(Arrays.asList("/test", "/test/path"),
                CuratorUtils.getParentPaths("/test/path/foo"));
        assertEquals(Arrays.asList("/test", "/test/path", "/test/path/foo"),
                CuratorUtils.getParentPaths("/test/path/foo/bar"));
        assertEquals(Arrays.asList("/test", "/test/path", "/test/path/foo", "/test/path/foo/bar"),
                CuratorUtils.getParentPaths("/test/path/foo/bar/baz"));
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
