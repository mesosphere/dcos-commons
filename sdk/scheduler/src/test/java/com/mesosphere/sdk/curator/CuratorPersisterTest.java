package com.mesosphere.sdk.curator;


import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.KeeperException;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;
import com.mesosphere.sdk.testutils.TestConstants;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CuratorPersister}.
 */
public class CuratorPersisterTest {
    @Mock private ServiceSpec mockServiceSpec;
    private static TestingServer testZk;

    // Paths passed to the interface:
    private static final String PATH_PARENT = "/path";
    private static final String PATH_1 = "/path/1";
    private static final String PATH_2 = "/path/2";
    private static final String PATH_SUB_PARENT = "/path/sub";
    private static final String PATH_SUB_1 = "/path/sub/1";
    private static final String PATH_SUB_2 = "/path/sub/2";

    // Paths accessed internally (where stuff actually goes, prefixed with dcos-service-<svcname>):
    private static final String INTERNAL_PATH_SERVICE = String.format("/dcos-service-%s", TestConstants.SERVICE_NAME);
    private static final List<String> UNPREFIXED_PATHS = Arrays.asList(
        INTERNAL_PATH_SERVICE, PATH_PARENT, PATH_1, PATH_2, PATH_SUB_PARENT,
        PATH_SUB_1, PATH_SUB_2);


    private static final byte[] DATA_1 = "one".getBytes(Charset.defaultCharset());
    private static final byte[] DATA_2 = "two".getBytes(Charset.defaultCharset());
    private static final byte[] DATA_SUB_1 = "sub_one".getBytes(Charset.defaultCharset());
    private static final byte[] DATA_SUB_2 = "sub_two".getBytes(Charset.defaultCharset());

    private static final Map<String, byte[]> SET_MANY_MAP = new TreeMap<>();
    static {
        SET_MANY_MAP.put(PATH_1, DATA_1);
        SET_MANY_MAP.put(PATH_2, DATA_2);
        SET_MANY_MAP.put(PATH_SUB_1, DATA_SUB_1);
        SET_MANY_MAP.put(PATH_SUB_2, DATA_SUB_2);
    }
    // the nested deletes will be autodetected, depending on the scenario
    private static final Collection<String> DELETE_MANY_LIST = Arrays.asList("/");

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        // easier than creating a real ServiceSpec just for two fields (zk connect string is set in tests):
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
    }

    @Test
    public void testStoreFolderedService() throws Exception {
        CuratorTestUtils.clear(testZk);
        String originalServiceName = "/folder/path/to/myservice";
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        when(mockServiceSpec.getName()).thenReturn(originalServiceName);
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        persister.set(PATH_1, DATA_1);
        assertArrayEquals(DATA_1, persister.get(PATH_1));
    }

    @Test
    public void testStoreUnfolderedService() throws Exception {
        CuratorTestUtils.clear(testZk);
        String originalServiceName = "myservice";
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        when(mockServiceSpec.getName()).thenReturn(originalServiceName);
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        persister.set(PATH_1, DATA_1);
        assertArrayEquals(DATA_1, persister.get(PATH_1));
    }

    @Test
    public void testSetManyAgainstEmpty() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        persister.setMany(SET_MANY_MAP);

        assertArrayEquals(DATA_1, persister.get(PATH_1));
        assertArrayEquals(DATA_2, persister.get(PATH_2));
        assertArrayEquals(DATA_SUB_1, persister.get(PATH_SUB_1));
        assertArrayEquals(DATA_SUB_2, persister.get(PATH_SUB_2));
    }

    @Test
    public void testSetManyAgainstOnesMissing() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupOnesMissing(persister);
        persister.setMany(SET_MANY_MAP);

        assertArrayEquals(DATA_1, persister.get(PATH_1));
        assertArrayEquals(DATA_2, persister.get(PATH_2));
        assertArrayEquals(DATA_SUB_1, persister.get(PATH_SUB_1));
        assertArrayEquals(DATA_SUB_2, persister.get(PATH_SUB_2));
    }


    @Test
    public void testSetManyAgainstRootsMissing() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupRootsMissing(persister);
        persister.setMany(SET_MANY_MAP);

        assertArrayEquals(DATA_1, persister.get(PATH_1));
        assertArrayEquals(DATA_2, persister.get(PATH_2));
        assertArrayEquals(DATA_SUB_1, persister.get(PATH_SUB_1));
        assertArrayEquals(DATA_SUB_2, persister.get(PATH_SUB_2));
    }

    @Test
    public void testSetManyAgainstSubsMissing() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupSubsMissing(persister);
        persister.setMany(SET_MANY_MAP);

        assertArrayEquals(DATA_1, persister.get(PATH_1));
        assertArrayEquals(DATA_2, persister.get(PATH_2));
        assertArrayEquals(DATA_SUB_1, persister.get(PATH_SUB_1));
        assertArrayEquals(DATA_SUB_2, persister.get(PATH_SUB_2));
    }

    @Test
    public void testSetManyAgainstFull() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupFull(persister);
        persister.setMany(SET_MANY_MAP);

        assertArrayEquals(DATA_1, persister.get(PATH_1));
        assertArrayEquals(DATA_2, persister.get(PATH_2));
        assertArrayEquals(DATA_SUB_1, persister.get(PATH_SUB_1));
        assertArrayEquals(DATA_SUB_2, persister.get(PATH_SUB_2));
    }

    @Test(expected = PersisterException.class)
    public void testDeleteManyAgainstEmpty() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupEmpty(persister);
        persister.recursiveDeleteMany(DELETE_MANY_LIST);
    }

    @Test
    public void testDeleteManyAgainstOnesMissing() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupOnesMissing(persister);
        persister.recursiveDeleteMany(DELETE_MANY_LIST);
    }

    @Test
    public void testDeleteManyAgainstRootsMissing() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupRootsMissing(persister);
        persister.recursiveDeleteMany(DELETE_MANY_LIST);
    }

    @Test
    public void testDeleteManyAgainstSubsMissing() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupSubsMissing(persister);
        persister.recursiveDeleteMany(DELETE_MANY_LIST);
    }

    @Test
    public void testDeleteManyAgainstFull() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupFull(persister);
        persister.recursiveDeleteMany(DELETE_MANY_LIST);
    }

    @Test(expected=PersisterException.class)
    public void testSetManyAgainstEmptyFails() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        setupEmpty(persister);
        persister.setMany(SET_MANY_MAP);
    }

    // Uses a real ZK instance to ensure that our integration works as expected:
    @Test
    public void testAclBehavior() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister nonAclPersister = CuratorPersister.newBuilder(mockServiceSpec)
                .disableLock()
                .build();
        Persister aclPersister = CuratorPersister.newBuilder(mockServiceSpec)
                .disableLock()
                .setCredentials("testuser", "testpw")
                .build();

        // Store value with ACL.
        aclPersister.set(PATH_1, DATA_1);

        // Readable with appropriate Auth and ACL.
        assertArrayEquals(DATA_1, aclPersister.get(PATH_1));

        // Readable with world:anyone permission.
        assertArrayEquals(DATA_1, nonAclPersister.get(PATH_1));

        // Not overwriteable with world:anyone permission.
        try {
            nonAclPersister.set(PATH_1, DATA_2);
            fail("Should have failed with auth exception");
        } catch (PersisterException e) {
            assertEquals(Reason.STORAGE_ERROR, e.getReason());
            assertTrue(e.getCause() instanceof KeeperException.NoAuthException);
        }

        // Not overwriteable with incorrect Auth
        try {
            Persister wrongAclPersister = CuratorPersister.newBuilder(mockServiceSpec)
                    .disableLock()
                    .setCredentials("testuser", "otherpw")
                    .build();
            wrongAclPersister.set(PATH_1, DATA_SUB_1);
            fail("Should have failed with auth exception");
        } catch ( PersisterException e ) {
            assertEquals(Reason.STORAGE_ERROR, e.getReason());
            assertTrue(e.getCause() instanceof KeeperException.NoAuthException);
        }

        // Delete ACL'ed data so that other tests don't have ACL problems trying to clear it:
        aclPersister.recursiveDelete(PATH_PARENT);
    }

    // Uses a real ZK instance to ensure that our integration works as expected:
    @Test
    public void testDeleteRoot() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();

        persister.set("lock", DATA_1);
        persister.set("a", DATA_2);
        persister.set("a/1", DATA_1);
        persister.set("a/lock", DATA_2);
        persister.set("a/2/a", DATA_1);
        persister.set("a/3", DATA_2);
        persister.set("a/3/a/1", DATA_1);
        persister.set("b", DATA_2);
        persister.set("c", DATA_1);
        persister.set("d/1/a/1", DATA_2);

        persister.recursiveDelete("");

        // The root-level "lock" is preserved (it's special):
        assertEquals(Collections.singleton("lock"), persister.getChildren(""));
        assertArrayEquals(DATA_1, persister.get("lock"));
        assertEquals(Collections.singleton("/lock"), PersisterUtils.getAllKeys(persister));
    }

    @Test
    public void testWriteServiceName() throws Exception {
        CuratorTestUtils.clear(testZk);
        String folderedName = "/path/to/myservice";
        when(mockServiceSpec.getName()).thenReturn(folderedName);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        assertEquals(Collections.singleton("servicename"), persister.getChildren(""));
        assertArrayEquals(folderedName.getBytes(StandardCharsets.UTF_8), persister.get("servicename"));
    }

    @Test
    public void testServiceNameDoubleUnderscore() throws Exception {
        CuratorTestUtils.clear(testZk);
        String folderedName = "/path/to__myservice";
        when(mockServiceSpec.getName()).thenReturn(folderedName);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        try {
            CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("double underscore"));
        }
    }

    @Test
    public void testRecursiveCopy() throws Exception {
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();

        persister.set("lock", DATA_1);
        persister.set("x", DATA_2);
        persister.set("x/1", DATA_1);
        persister.set("x/lock", DATA_2);
        persister.set("x/2/a", DATA_1);
        persister.set("x/3", DATA_2);
        persister.set("x/3/a/1", DATA_1);
        persister.set("x/5/1", null);
        persister.set("y", DATA_2);
        persister.set("z", DATA_1);
        persister.set("w/1/a/1", DATA_2);
        persister.set("w/1/a/2", null);

        persister.recursiveCopy("/x", "/p");

        assertArrayEquals(new String[]{"1", "2", "3", "5", "lock"}, persister.getChildren("/p").toArray());
        assertTrue(persister.getChildren("/p/1").isEmpty());
        assertTrue(persister.getChildren("/p/lock").isEmpty());
        assertArrayEquals(new String[]{"a"}, persister.getChildren("/p/2").toArray());
        assertArrayEquals(new String[]{"a"}, persister.getChildren("/p/3").toArray());
        assertArrayEquals(new String[]{"1"}, persister.getChildren("/p/3/a").toArray());
        assertArrayEquals(new String[]{"1"}, persister.getChildren("/p/5").toArray());
        assertArrayEquals(DATA_2, persister.get("p"));
        assertArrayEquals(DATA_1, persister.get("p/1"));
        assertArrayEquals(DATA_2, persister.get("p/lock"));
        assertArrayEquals(DATA_1, persister.get("p/2/a"));
        assertArrayEquals(DATA_2, persister.get("p/3"));
        assertArrayEquals(DATA_1, persister.get("p/3/a/1"));
        assertArrayEquals(null, persister.get("p/5/1"));
    }

    @Test(expected = PersisterException.class)
    public void recursiveCopyShouldFailIfTargetExists() throws Exception{
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        persister.set("x", DATA_2);
        persister.set("y", DATA_1);
        persister.recursiveCopy("/x", "/y");
    }

    @Test(expected = PersisterException.class)
    public void recursiveCopyShouldFailIfSourceDoesNotExist() throws Exception{
        CuratorTestUtils.clear(testZk);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        persister.set("y", DATA_1);
        persister.recursiveCopy("/x", "/y");
    }

    @Test(expected = PersisterException.class)
    public void recursiveCopyShouldFailIfSourceEqualsDestination() throws Exception{
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        Persister persister = CuratorPersister.newBuilder(mockServiceSpec).disableLock().build();
        persister.recursiveCopy("/x", "/x");
    }

    @Test(expected = IllegalArgumentException.class)
    public void recursiveCopyShouldFailOnIllegalSource() throws Exception{
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        CuratorPersister
                .newBuilder(mockServiceSpec)
                .disableLock()
                .build()
                .recursiveCopy(CuratorLocker.LOCK_PATH_NAME, "/does-not-matter");
    }

    @Test(expected = IllegalArgumentException.class)
    public void recursiveCopyShouldFailOnIllegalTarget() throws Exception{
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());
        CuratorPersister
                .newBuilder(mockServiceSpec)
                .disableLock()
                .build()
                .recursiveCopy("/does-not-matter", INTERNAL_PATH_SERVICE);
    }

    private void setupEmpty(Persister persister) throws Exception {
        setupFull(persister);
        for (String path : UNPREFIXED_PATHS) {
            persister.recursiveDelete(path);
        }
    }

    private void setupFull(Persister persister) throws Exception {
        for (String path : UNPREFIXED_PATHS) {
            persister.set(path, DATA_1);
        }
    }

    private void setupOnesMissing(Persister persister) throws Exception {
        setupFull(persister);
        persister.set(PATH_1, null);
        persister.set(PATH_SUB_1, null);
    }

    private void setupRootsMissing(Persister persister) throws Exception {
        setupFull(persister);
        persister.set(PATH_PARENT, null);
        persister.set(PATH_1, null);
        persister.set(PATH_2, null);
    }

    private void setupSubsMissing(Persister persister) throws Exception {
        setupFull(persister);
        persister.set(PATH_SUB_PARENT, null);
        persister.set(PATH_SUB_1, null);
        persister.set(PATH_SUB_2, null);
    }
}
