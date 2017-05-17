package com.mesosphere.sdk.storage;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

/**
 * Tests for {@link PersisterUtils}.
 */
public class PersisterUtilsTest {

    @Test
    public void testJoinPath() {
        assertEquals("test/path", PersisterUtils.join("test", "path"));
        assertEquals("test/path", PersisterUtils.join("test", "/path"));
        assertEquals("test/path", PersisterUtils.join("test/", "path"));
        assertEquals("test/path", PersisterUtils.join("test/", "/path"));
        assertEquals("test//path", PersisterUtils.join("test//", "/path"));
        assertEquals("test//path", PersisterUtils.join("test/", "//path"));

        assertEquals("/test/path", PersisterUtils.join("/test", "path"));
        assertEquals("/test/path", PersisterUtils.join("/test", "/path"));
        assertEquals("/test/path", PersisterUtils.join("/test/", "path"));
        assertEquals("/test/path", PersisterUtils.join("/test/", "/path"));
        assertEquals("/test//path", PersisterUtils.join("/test//", "/path"));
        assertEquals("/test//path", PersisterUtils.join("/test/", "//path"));
    }

    @Test
    public void testGetParentPaths() {
        assertEquals(new ArrayList<String>(), PersisterUtils.getParentPaths(""));
        assertEquals(new ArrayList<String>(), PersisterUtils.getParentPaths("test"));
        assertEquals(new ArrayList<String>(), PersisterUtils.getParentPaths("test/"));
        assertEquals(Arrays.asList("test"), PersisterUtils.getParentPaths("test/path"));
        assertEquals(Arrays.asList("test", "test/path"), PersisterUtils.getParentPaths("test/path/foo"));
        assertEquals(Arrays.asList("test", "test/path", "test/path/foo"),
                PersisterUtils.getParentPaths("test/path/foo/bar"));
        assertEquals(Arrays.asList("test", "test/path", "test/path/foo", "test/path/foo/bar"),
                PersisterUtils.getParentPaths("test/path/foo/bar/baz"));
    }

    @Test
    public void testGetParentPathsLeadingSlash() {
        assertEquals(new ArrayList<String>(), PersisterUtils.getParentPaths("/"));
        assertEquals(new ArrayList<String>(), PersisterUtils.getParentPaths("//"));
        assertEquals(new ArrayList<String>(), PersisterUtils.getParentPaths("/test"));
        assertEquals(new ArrayList<String>(), PersisterUtils.getParentPaths("/test/"));
        assertEquals(Arrays.asList("/test"), PersisterUtils.getParentPaths("/test/path"));
        assertEquals(Arrays.asList("/test", "/test/path"), PersisterUtils.getParentPaths("/test/path/foo"));
        assertEquals(Arrays.asList("/test", "/test/path", "/test/path/foo"),
                PersisterUtils.getParentPaths("/test/path/foo/bar"));
        assertEquals(Arrays.asList("/test", "/test/path", "/test/path/foo", "/test/path/foo/bar"),
                PersisterUtils.getParentPaths("/test/path/foo/bar/baz"));
    }
}
