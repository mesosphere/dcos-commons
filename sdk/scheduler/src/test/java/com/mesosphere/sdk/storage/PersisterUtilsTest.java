package com.mesosphere.sdk.storage;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

    @Test
    public void testGetAllKeys() throws PersisterException {
        Persister persister = new MemPersister();
        Map<String, byte[]> map = new HashMap<>();
        byte[] data = new byte[0];
        map.put("/a", data);
        map.put("/a/1", data);
        map.put("/a/2/a", data);
        map.put("/a/3", data);
        map.put("/a/3/a/1", data);
        map.put("/b", data);
        map.put("/c", data);
        map.put("/d/1/a/1", data);
        persister.setMany(map);

        Set<String> expected = new TreeSet<>();
        expected.add("/a");
        expected.add("/a/1");
        expected.add("/a/2");
        expected.add("/a/2/a");
        expected.add("/a/3");
        expected.add("/a/3/a");
        expected.add("/a/3/a/1");
        expected.add("/b");
        expected.add("/c");
        expected.add("/d");
        expected.add("/d/1");
        expected.add("/d/1/a");
        expected.add("/d/1/a/1");
        assertEquals(expected, PersisterUtils.getAllKeys(persister));
    }

    @Test
    public void testGetAllData() throws PersisterException {
        Persister persister = new MemPersister();
        Map<String, byte[]> map = new HashMap<>();
        byte[] data = new byte[0];
        map.put("/a", data);
        map.put("/a/1", data);
        map.put("/a/2/a", data);
        map.put("/a/3", data);
        map.put("/a/3/a/1", data);
        map.put("/b", data);
        map.put("/c", data);
        map.put("/d/1/a/1", data);
        persister.setMany(map);

        Map<String, byte[]> expected = new TreeMap<>();
        expected.putAll(map);
        assertEquals(expected, PersisterUtils.getAllData(persister));
    }
}
