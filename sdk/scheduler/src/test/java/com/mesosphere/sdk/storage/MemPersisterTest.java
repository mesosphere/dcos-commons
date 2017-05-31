package com.mesosphere.sdk.storage;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.apache.curator.test.TestingServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.curator.CuratorTestUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.StorageError.Reason;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * Tests for {@link MemPersister}
 */
public class MemPersisterTest {
    private static final String KEY = "key";
    private static final byte[] VAL = "someval".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VAL2 = "someval2".getBytes(StandardCharsets.UTF_8);

    @Mock private ServiceSpec mockServiceSpec;
    private static TestingServer testZk;

    private Persister persister;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        persister = new MemPersister();
    }

    @Test
    public void testGetMissing() throws Exception {
        // Run the same test against a real ZK persister to validate that the MemPersister behavior matches real ZK:
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        CuratorTestUtils.clear(testZk);
        testGetMissingForPersister(CuratorPersister.newBuilder(mockServiceSpec).build());
        testGetMissingForPersister(persister);
    }

    private static void testGetMissingForPersister(Persister persister) {
        try {
            persister.get(KEY);
            fail("expected exception");
        } catch (PersisterException e) {
            // expected
        }
    }

    @Test
    public void testGetMissingRoot() throws Exception {
        // Run the same test against a real ZK persister to validate that the MemPersister behavior matches real ZK:
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        CuratorTestUtils.clear(testZk);
        testGetMissingRootForPersister(CuratorPersister.newBuilder(mockServiceSpec).build());
        testGetMissingRootForPersister(persister);
    }

    private static void testGetMissingRootForPersister(Persister persister) throws PersisterException {
        assertArrayEquals(null, persister.get(""));
    }

    @Test(expected = PersisterException.class)
    public void testDeleteMissing() throws PersisterException {
        persister.deleteAll(KEY);
    }

    @Test
    public void testGetChildren() throws Exception {
        // Run the same test against a real ZK persister to validate that the MemPersister behavior matches real ZK:
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        CuratorTestUtils.clear(testZk);
        testGetChildrenForPersister(CuratorPersister.newBuilder(mockServiceSpec).build());
        testGetChildrenForPersister(persister);
    }

    private static void testGetChildrenForPersister(Persister persister) throws PersisterException {
        persister.set("/a", VAL);
        persister.set("/a/1", VAL);
        persister.set("/a/2/a", VAL);
        persister.set("/a/3", VAL);
        persister.set("/a/3/a/1", VAL);
        persister.set("/b", VAL);
        persister.set("/c", VAL);
        persister.set("/d/1/a/1", VAL);
        checkNotFound(persister, "notfound");
        checkChildren(Arrays.asList("a", "b", "c", "d"), persister, "");

        checkChildren(Arrays.asList("1", "2", "3"), persister, "a");
        checkNotFound(persister, "a/notfound");
        checkChildren(Collections.emptyList(), persister, "a/1");
        checkChildren(Arrays.asList("a"), persister, "a/2");
        checkChildren(Collections.emptyList(), persister, "a/2/a");
        checkChildren(Arrays.asList("a"), persister, "a/3");
        checkNotFound(persister, "a/3/notfound");
        checkChildren(Arrays.asList("1"), persister, "a/3/a");
        checkNotFound(persister, "a/3/a/notfound");
        checkChildren(Collections.emptyList(), persister, "a/3/a/1");
        checkNotFound(persister, "a/3/a/1/notfound");

        checkChildren(Collections.emptyList(), persister, "b");

        checkChildren(Collections.emptyList(), persister, "c");

        checkChildren(Arrays.asList("1"), persister, "d");
        checkChildren(Arrays.asList("a"), persister, "d/1");
        checkChildren(Arrays.asList("1"), persister, "d/1/a");
        checkChildren(Collections.emptyList(), persister, "d/1/a/1");
    }

    @Test
    public void testGetChildrenMissingRoot() throws Exception {
        // Run the same test against a real ZK persister to validate that the MemPersister behavior matches real ZK:
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        CuratorTestUtils.clear(testZk);
        testGetChildrenEmptyRootForPersister(CuratorPersister.newBuilder(mockServiceSpec).build());
        testGetChildrenEmptyRootForPersister(persister);
    }

    private static void testGetChildrenEmptyRootForPersister(Persister persister) throws PersisterException {
        assertTrue(persister.getChildren("").isEmpty());
        assertTrue(persister.getChildren("/").isEmpty());
    }

    @Test
    public void testDeleteChildren() throws Exception {
        // Run the same test against a real ZK persister to validate that the MemPersister behavior matches real ZK:
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        CuratorTestUtils.clear(testZk);
        testDeleteChildrenForPersister(CuratorPersister.newBuilder(mockServiceSpec).build());
        testDeleteChildrenForPersister(persister);
    }

    private static void testDeleteChildrenForPersister(Persister persister) throws Exception {
        persister.set("/a", VAL);
        persister.set("/a/1", VAL);
        persister.set("/a/2/a", VAL);
        persister.set("/a/3", VAL);
        persister.set("/a/3/a/1", VAL);
        persister.set("/b", VAL);
        persister.set("/c", VAL);
        persister.set("/d/1/a/1", VAL);

        persister.deleteAll("/a/1");
        checkChildren(Arrays.asList("2", "3"), persister, "a");
        persister.deleteAll("/a/3/a");
        checkChildren(Arrays.asList("2", "3"), persister, "a");
        persister.deleteAll("/b");
        checkChildren(Arrays.asList("a", "c", "d"), persister, "");
        persister.deleteAll("/c");
        checkChildren(Arrays.asList("a", "d"), persister, "");
        persister.deleteAll("/d/1");
        checkChildren(Arrays.asList("a", "d"), persister, "");
        persister.deleteAll("/a");
        checkChildren(Arrays.asList("d"), persister, "");
        persister.deleteAll("/d");
        checkChildren(Collections.emptyList(), persister, "");
    }

    @Test
    public void testDeleteRoot() throws Exception {
        // Run the same test against a real ZK persister to validate that the MemPersister behavior matches real ZK:
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        testDeleteRootForPersister(persister, "");
        testDeleteRootForPersister(persister, "/");
        CuratorTestUtils.clear(testZk);
        testDeleteRootForPersister(CuratorPersister.newBuilder(mockServiceSpec).build(), "");
        CuratorTestUtils.clear(testZk);
        testDeleteRootForPersister(CuratorPersister.newBuilder(mockServiceSpec).build(), "/");
    }

    private static void testDeleteRootForPersister(Persister persister, String rootPathToDelete) throws Exception {
        assertEquals(null, persister.get(""));
        assertTrue(persister.getChildren("").isEmpty());

        persister.set("/a", VAL);
        persister.set("/a/1", VAL);
        persister.set("/a/2/a", VAL);
        persister.set("/a/3", VAL);
        persister.set("/a/3/a/1", VAL);
        persister.set("/b", VAL);
        persister.set("/c", VAL);
        persister.set("/d/1/a/1", VAL);

        persister.deleteAll(rootPathToDelete);

        byte[] dat = persister.get("");
        String desc = dat == null ? "NULL" : String.format("%d bytes", dat.length);
        assertEquals(desc, null, dat);
        assertTrue(persister.getChildren("").isEmpty());
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
                            persister.set(key, VAL);
                            assertArrayEquals(VAL, persister.get(key));
                            persister.set(key, VAL2);
                            assertArrayEquals(VAL2, persister.get(key));
                            persister.setMany(Collections.singletonMap(key, VAL));
                            assertArrayEquals(VAL, persister.get(key));
                            persister.deleteAll(key);
                            try {
                                persister.get(key);
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

    private static void checkChildren(Collection<String> expected, Persister persister, String path)
            throws PersisterException {
        expected = new TreeSet<>(expected); // ensure types match for assert calls
        assertEquals(path, expected, persister.getChildren(path));
        assertEquals("/" + path, expected, persister.getChildren("/" + path));
        assertEquals(path + "/", expected, persister.getChildren(path + "/"));
        assertEquals("/" + path + "/", expected, persister.getChildren("/" + path + "/"));
    }

    private static void checkNotFound(Persister persister, String path) {
        try {
            persister.getChildren(path);
            fail("expected exception");
        } catch (PersisterException e) {
            assertEquals(Reason.NOT_FOUND, e.getReason());
        }
        try {
            persister.getChildren("/" + path);
            fail("expected exception");
        } catch (PersisterException e) {
            assertEquals(Reason.NOT_FOUND, e.getReason());
        }
        try {
            persister.getChildren(path + "/");
            fail("expected exception");
        } catch (PersisterException e) {
            assertEquals(Reason.NOT_FOUND, e.getReason());
        }
        try {
            persister.getChildren("/" + path + "/");
            fail("expected exception");
        } catch (PersisterException e) {
            assertEquals(Reason.NOT_FOUND, e.getReason());
        }
    }
}
