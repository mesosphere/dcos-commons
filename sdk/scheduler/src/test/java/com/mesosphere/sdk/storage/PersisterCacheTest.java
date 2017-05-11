package com.mesosphere.sdk.storage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.state.PathUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PersisterCache}
 */
public class PersisterCacheTest {

    private static final String KEY = "key";
    private static final byte[] VAL = "someval".getBytes(StandardCharsets.UTF_8);
    private static final String KEY2 = "key2";
    private static final byte[] VAL2 = "someval2".getBytes(StandardCharsets.UTF_8);

    private static final Collection<String> KEY_SET = new TreeSet<>(Arrays.asList("/" + KEY));
    private static final Collection<String> KEY2_SET = new TreeSet<>(Arrays.asList("/" + KEY2));
    private static final Collection<String> BOTH_KEYS_SET = new TreeSet<>(Arrays.asList("/" + KEY, "/" + KEY2));

    @Mock private Persister mockPersister;
    private Persister persister;
    private PersisterCache cache;

    @Before
    public void beforeEach() throws Exception {
        persister = new MemPersister();
        cache = new PersisterCache(persister);

        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInitEmpty() throws PersisterException {
        cache = new PersisterCache(persister);
        assertTrue(getAllKeys(cache).isEmpty());
    }

    @Test
    public void testInitNonEmpty() throws PersisterException {
        persister.set(KEY, VAL);
        persister.set(KEY2, VAL2);
        cache = new PersisterCache(persister); // recreate with non-empty persister
        assertEquals(BOTH_KEYS_SET, getAllKeys(cache));
    }

    @Test
    public void testSetGetDelete() throws PersisterException {
        cache.set(KEY, VAL);
        assertArrayEquals(VAL, cache.get(KEY));
        assertEquals(KEY_SET, getAllKeys(persister));
        assertEquals(KEY_SET, getAllKeys(cache));

        cache.set(KEY2, VAL2);
        assertArrayEquals(VAL2, cache.get(KEY2));
        assertEquals(BOTH_KEYS_SET, getAllKeys(persister));
        assertEquals(BOTH_KEYS_SET, getAllKeys(cache));

        cache.delete(KEY);
        try {
            cache.get(KEY);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
        assertEquals(KEY2_SET, getAllKeys(persister));
        assertEquals(KEY2_SET, getAllKeys(cache));

        cache.delete(KEY2);
        try {
            cache.get(KEY2);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
        assertTrue(getAllKeys(persister).isEmpty());
        assertTrue(getAllKeys(cache).isEmpty());
    }

    @Test
    public void testSetManyGetDelete() throws PersisterException {
        Map<String, byte[]> map = new HashMap<>();
        map.put(KEY, VAL);
        cache.setMany(map);

        assertArrayEquals(VAL, cache.get(KEY));
        assertEquals(KEY_SET, getAllKeys(persister));
        assertEquals(KEY_SET, getAllKeys(cache));

        map.put(KEY, VAL2); // overwrite prior value
        map.put(KEY2, VAL2);
        cache.setMany(map);

        assertArrayEquals(VAL2, cache.get(KEY));
        assertArrayEquals(VAL2, cache.get(KEY2));
        assertEquals(BOTH_KEYS_SET, getAllKeys(persister));
        assertEquals(BOTH_KEYS_SET, getAllKeys(cache));

        cache.delete(KEY);
        cache.delete(KEY2);

        assertTrue(getAllKeys(persister).isEmpty());
        assertTrue(getAllKeys(cache).isEmpty());
    }

    @Test
    public void testSetFailsCacheUnchanged() throws PersisterException {
        when(mockPersister.getChildren(Mockito.anyString())).thenReturn(Collections.emptyList());
        doThrow(new PersisterException(Reason.STORAGE_ERROR, "hi"))
                .when(mockPersister).set(Mockito.eq(KEY2), Mockito.anyObject());
        cache = new PersisterCache(mockPersister);

        cache.set(KEY, VAL);
        try {
            cache.set(KEY2, VAL2);
            fail("Expected exception");
        } catch (PersisterException e) {
            // expected
        }
        assertEquals(KEY_SET, getAllKeys(cache));
        assertArrayEquals(VAL, cache.get(KEY));
        try {
            cache.get(KEY2);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
    }

    @Test
    public void testSetManyFailsCacheUnchanged() throws PersisterException {
        Map<String, byte[]> map = new HashMap<>();
        map.put(KEY, VAL);
        map.put(KEY2, VAL2);

        when(mockPersister.getChildren(Mockito.anyString())).thenReturn(Collections.emptyList());
        doThrow(new PersisterException(Reason.STORAGE_ERROR, "hi"))
                .when(mockPersister).setMany(Mockito.anyObject());
        cache = new PersisterCache(mockPersister);

        try {
            cache.setMany(map);
            fail("Expected exception");
        } catch (PersisterException e) {
            // expected
        }
        assertTrue(getAllKeys(cache).isEmpty());
        try {
            cache.get(KEY);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
        try {
            cache.get(KEY2);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
    }

    @Test
    public void testDeleteFailsCacheUnchanged() throws PersisterException {
        when(mockPersister.getChildren(Mockito.anyString())).thenReturn(Collections.emptyList());
        doThrow(new PersisterException(Reason.STORAGE_ERROR, "hi"))
                .when(mockPersister).delete(KEY2);
        cache = new PersisterCache(mockPersister);
        cache.set(KEY, VAL);
        cache.set(KEY2, VAL2);

        cache.delete(KEY);
        try {
            cache.delete(KEY2);
            fail("Expected exception");
        } catch (PersisterException e) {
            // expected
        }
        assertEquals(KEY2_SET, getAllKeys(cache));
        assertArrayEquals(VAL2, cache.get(KEY2));
        try {
            cache.get(KEY);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
    }

    @Test
    public void testMultithreadedSetGetDelete() throws InterruptedException {
        Collection<Runnable> threads = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            final String key = String.format("%s-%d", KEY, i);
            threads.add(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int i = 0; i < 100; ++i) {
                            cache.set(key, VAL);
                            assertArrayEquals(VAL, cache.get(key));
                            cache.set(key, VAL2);
                            assertArrayEquals(VAL2, cache.get(key));
                            cache.setMany(Collections.singletonMap(key, VAL));
                            assertArrayEquals(VAL, cache.get(key));
                            cache.delete(key);
                            try {
                                cache.get(key);
                                fail("Expected exception");
                            } catch (PersisterException e) {
                                // expected
                            }
                        }
                    } catch (PersisterException e) {
                        fail(e.getMessage());
                    }
                }
            });
        }
        runThreads(threads);
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
        assertEquals(expected, getAllKeys(persister));
    }

    private static void runThreads(Collection<Runnable> runnables) throws InterruptedException {
        final Object lock = new Object();
        final List<Throwable> errors = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (Runnable runnable : runnables) {
            Thread t = new Thread(runnable);
            t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    synchronized (lock) {
                        errors.add(e);
                    }
                }
            });
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            t.join();
        }
        assertTrue(errors.toString(), errors.isEmpty());
    }

    /**
     * Returns a list of all keys within the provided {@link Persister}.
     */
    private static Collection<String> getAllKeys(Persister persister) throws PersisterException {
        return getAllKeysUnder(persister, PathUtils.PATH_DELIM);
    }

    private static Collection<String> getAllKeysUnder(Persister persister, String path) throws PersisterException {
        Collection<String> allKeys = new TreeSet<>(); // consistent ordering (mainly for tests)
        for (String child : persister.getChildren(path)) {
            String childPath = PathUtils.join(path, child);
            allKeys.add(childPath);
            allKeys.addAll(getAllKeysUnder(persister, childPath)); // RECURSE
        }
        return allKeys;
    }
}
