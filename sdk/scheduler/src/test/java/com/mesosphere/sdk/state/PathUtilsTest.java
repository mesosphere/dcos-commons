package com.mesosphere.sdk.state;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

/**
 * Tests for {@link PathUtils}.
 */
public class PathUtilsTest {

    @Test
    public void testJoinPath() {
        assertEquals("test/path", PathUtils.join("test", "path"));
        assertEquals("test/path", PathUtils.join("test", "/path"));
        assertEquals("test/path", PathUtils.join("test/", "path"));
        assertEquals("test/path", PathUtils.join("test/", "/path"));
        assertEquals("test//path", PathUtils.join("test//", "/path"));
        assertEquals("test//path", PathUtils.join("test/", "//path"));

        assertEquals("/test/path", PathUtils.join("/test", "path"));
        assertEquals("/test/path", PathUtils.join("/test", "/path"));
        assertEquals("/test/path", PathUtils.join("/test/", "path"));
        assertEquals("/test/path", PathUtils.join("/test/", "/path"));
        assertEquals("/test//path", PathUtils.join("/test//", "/path"));
        assertEquals("/test//path", PathUtils.join("/test/", "//path"));
    }

    @Test
    public void testGetParentPaths() {
        assertEquals(new ArrayList<String>(), PathUtils.getParentPaths(""));
        assertEquals(new ArrayList<String>(), PathUtils.getParentPaths("test"));
        assertEquals(Arrays.asList("test"), PathUtils.getParentPaths("test/path"));
        assertEquals(Arrays.asList("test", "test/path"), PathUtils.getParentPaths("test/path/foo"));
        assertEquals(Arrays.asList("test", "test/path", "test/path/foo"),
                PathUtils.getParentPaths("test/path/foo/bar"));
        assertEquals(Arrays.asList("test", "test/path", "test/path/foo", "test/path/foo/bar"),
                PathUtils.getParentPaths("test/path/foo/bar/baz"));

        assertEquals(new ArrayList<String>(), PathUtils.getParentPaths("/"));
        assertEquals(new ArrayList<String>(), PathUtils.getParentPaths("/test"));
        assertEquals(Arrays.asList("/test"), PathUtils.getParentPaths("/test/path"));
        assertEquals(Arrays.asList("/test", "/test/path"), PathUtils.getParentPaths("/test/path/foo"));
        assertEquals(Arrays.asList("/test", "/test/path", "/test/path/foo"),
                PathUtils.getParentPaths("/test/path/foo/bar"));
        assertEquals(Arrays.asList("/test", "/test/path", "/test/path/foo", "/test/path/foo/bar"),
                PathUtils.getParentPaths("/test/path/foo/bar/baz"));
    }
}
