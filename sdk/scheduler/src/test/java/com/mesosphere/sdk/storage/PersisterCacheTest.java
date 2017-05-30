package com.mesosphere.sdk.storage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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
        assertTrue(PersisterUtils.getAllKeys(cache).isEmpty());
    }

    @Test
    public void testInitBasic() throws PersisterException {
        persister.set(KEY, VAL);
        persister.set(KEY2, VAL2);
        cache = new PersisterCache(persister); // recreate with non-empty persister
        assertEquals(BOTH_KEYS_SET, PersisterUtils.getAllKeys(cache));
    }

    @Test
    public void testInitComplicated() throws PersisterException {
        Map<String, byte[]> dataToAdd = new TreeMap<>();
        dataToAdd.put("ConfigTarget", VAL);
        dataToAdd.put("Configurations/abad-coffee", VAL2);
        dataToAdd.put("FrameworkID", VAL);
        dataToAdd.put("Properties/suppressed", VAL2);
        dataToAdd.put("SchemaVersion", VAL);
        dataToAdd.put("Tasks/node-0/TaskInfo", VAL2);
        dataToAdd.put("Tasks/node-0/TaskStatus", VAL);
        dataToAdd.put("Tasks/node-1/TaskInfo", VAL2);
        dataToAdd.put("Tasks/node-1/TaskStatus", VAL);
        dataToAdd.put("Tasks/node-2/TaskInfo", VAL2);
        dataToAdd.put("Tasks/node-2/TaskStatus", VAL);

        Map<String, byte[]> expectedData = new TreeMap<>(); // keys get a slash added to the front
        for (Map.Entry<String, byte[]> entry : dataToAdd.entrySet()) {
            persister.set(entry.getKey(), entry.getValue());
            expectedData.put("/" + entry.getKey(), entry.getValue());
        }
        cache = new PersisterCache(persister); // recreate with non-empty persister

        assertEquals(expectedData, PersisterUtils.getAllData(cache));

        Set<String> expectedKeys = new TreeSet<>(); // parent keys are included in keys list
        expectedKeys.add("/ConfigTarget");
        expectedKeys.add("/Configurations");
        expectedKeys.add("/Configurations/abad-coffee");
        expectedKeys.add("/FrameworkID");
        expectedKeys.add("/Properties");
        expectedKeys.add("/Properties/suppressed");
        expectedKeys.add("/SchemaVersion");
        expectedKeys.add("/Tasks");
        expectedKeys.add("/Tasks/node-0");
        expectedKeys.add("/Tasks/node-0/TaskInfo");
        expectedKeys.add("/Tasks/node-0/TaskStatus");
        expectedKeys.add("/Tasks/node-1");
        expectedKeys.add("/Tasks/node-1/TaskInfo");
        expectedKeys.add("/Tasks/node-1/TaskStatus");
        expectedKeys.add("/Tasks/node-2");
        expectedKeys.add("/Tasks/node-2/TaskInfo");
        expectedKeys.add("/Tasks/node-2/TaskStatus");
        assertEquals(expectedKeys, PersisterUtils.getAllKeys(cache));
    }

    @Test
    public void testSetGetDelete() throws PersisterException {
        cache.set(KEY, VAL);
        assertArrayEquals(VAL, cache.get(KEY));
        assertEquals(KEY_SET, PersisterUtils.getAllKeys(persister));
        assertEquals(KEY_SET, PersisterUtils.getAllKeys(cache));

        cache.set(KEY2, VAL2);
        assertArrayEquals(VAL2, cache.get(KEY2));
        assertEquals(BOTH_KEYS_SET, PersisterUtils.getAllKeys(persister));
        assertEquals(BOTH_KEYS_SET, PersisterUtils.getAllKeys(cache));

        cache.deleteAll(KEY);
        try {
            cache.get(KEY);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
        assertEquals(KEY2_SET, PersisterUtils.getAllKeys(persister));
        assertEquals(KEY2_SET, PersisterUtils.getAllKeys(cache));

        cache.deleteAll(KEY2);
        try {
            cache.get(KEY2);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
        assertTrue(PersisterUtils.getAllKeys(persister).isEmpty());
        assertTrue(PersisterUtils.getAllKeys(cache).isEmpty());
    }

    @Test
    public void testSetManyGetDelete() throws PersisterException {
        Map<String, byte[]> map = new HashMap<>();
        map.put(KEY, VAL);
        cache.setMany(map);

        assertArrayEquals(VAL, cache.get(KEY));
        assertEquals(KEY_SET, PersisterUtils.getAllKeys(persister));
        assertEquals(KEY_SET, PersisterUtils.getAllKeys(cache));

        map.put(KEY, VAL2); // overwrite prior value
        map.put(KEY2, VAL2);
        cache.setMany(map);

        assertArrayEquals(VAL2, cache.get(KEY));
        assertArrayEquals(VAL2, cache.get(KEY2));
        assertEquals(BOTH_KEYS_SET, PersisterUtils.getAllKeys(persister));
        assertEquals(BOTH_KEYS_SET, PersisterUtils.getAllKeys(cache));

        cache.deleteAll(KEY);
        cache.deleteAll(KEY2);

        assertTrue(PersisterUtils.getAllKeys(persister).isEmpty());
        assertTrue(PersisterUtils.getAllKeys(cache).isEmpty());
    }

    @Test(expected = PersisterException.class)
    public void testCloseEmptiesCache() throws PersisterException {
        cache.set(KEY, VAL);
        assertArrayEquals(VAL, cache.get(KEY));

        cache.close();

        assertTrue(PersisterUtils.getAllKeys(persister).isEmpty());
        assertTrue(PersisterUtils.getAllKeys(cache).isEmpty());
        cache.get(KEY);
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
        assertEquals(KEY_SET, PersisterUtils.getAllKeys(cache));
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
        assertTrue(PersisterUtils.getAllKeys(cache).isEmpty());
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
                .when(mockPersister).deleteAll(KEY2);
        cache = new PersisterCache(mockPersister);
        cache.set(KEY, VAL);
        cache.set(KEY2, VAL2);
        assertEquals(BOTH_KEYS_SET, PersisterUtils.getAllKeys(cache));

        cache.deleteAll(KEY);
        try {
            cache.deleteAll(KEY2);
            fail("Expected exception");
        } catch (PersisterException e) {
            // expected
        }
        assertEquals(KEY2_SET, PersisterUtils.getAllKeys(cache));
        assertArrayEquals(VAL2, cache.get(KEY2));
        try {
            cache.get(KEY);
            fail("Expected exception");
        } catch (Exception e) {
            // expected, continue testing
        }
    }

    @Test
    public void testDeleteDidntFailAsExpectedCacheDoesntThrow() throws PersisterException {
        when(mockPersister.getChildren(Mockito.anyString())).thenReturn(Collections.emptyList());
        cache = new PersisterCache(mockPersister);
        cache.set(KEY, VAL);
        cache.set(KEY2, VAL2);
        assertEquals(BOTH_KEYS_SET, PersisterUtils.getAllKeys(cache));

        cache.deleteAll(KEY);
        cache.deleteAll(KEY2);
        assertTrue(PersisterUtils.getAllKeys(cache).isEmpty());

        // mockPersister doesn't throw despite missing keys. cache then logs error but doesn't throw (noop):
        cache.deleteAll(KEY);
        cache.deleteAll(KEY2);
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
                            cache.deleteAll(key);
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
}
