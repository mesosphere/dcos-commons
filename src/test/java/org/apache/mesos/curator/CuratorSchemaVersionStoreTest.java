package org.apache.mesos.curator;

import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.state.SchemaVersionStore;
import org.apache.mesos.state.StateStoreException;
import org.apache.mesos.storage.CuratorPersister;
import org.apache.mesos.testutils.CuratorTestUtils;
import org.apache.zookeeper.KeeperException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class CuratorSchemaVersionStoreTest {
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String ROOT_ZK_PATH = "/test-root-path";
    private static final String PREFIXED_ROOT_ZK_PATH = "/dcos-service-test-root-path";
    // This value must never change. If you're changing it, you're wrong:
    private static final String NODE_PATH = PREFIXED_ROOT_ZK_PATH + "/SchemaVersion";

    private static TestingServer testZk;
    private CuratorPersister curator;
    @Mock CuratorPersister mockCurator;
    private SchemaVersionStore store;
    private SchemaVersionStore store2;
    private SchemaVersionStore storeWithMock;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testZk);
        curator = new CuratorPersister(
                testZk.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        store = new CuratorSchemaVersionStore(curator, ROOT_ZK_PATH);
        store2 = new CuratorSchemaVersionStore(curator, ROOT_ZK_PATH);
        storeWithMock = new CuratorSchemaVersionStore(mockCurator, ROOT_ZK_PATH);
    }

    @Test
    public void testRootPathMapping() throws Exception {
        store.fetch();
        CuratorPersister curator = new CuratorPersister(
                testZk.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        assertNotEquals(0, curator.fetch("/dcos-service-test-root-path/SchemaVersion").length);
    }

    @Test
    public void testFetchAutoInitialize() throws Exception {
        // not initialized until first fetch:
        assertFalse(directHasVersion());

        assertEquals(1, store.fetch());

        // check that underlying storage now has the data:
        assertEquals(1, getDirectVersion());
    }

    @Test
    public void testStoreFetchStoreFetch() throws Exception {
        assertFalse(directHasVersion());
        final int val = 5;

        store.store(val);
        assertEquals(val, getDirectVersion());
        assertEquals(val, store.fetch());

        store.store(val + 1);
        assertEquals(val + 1, getDirectVersion());
        assertEquals(val + 1, store.fetch());
    }

    @Test
    public void testStoreFetchStoreFetch_acrossInstances() throws Exception {
        assertFalse(directHasVersion());
        final int val = 5;

        store.store(val);
        assertEquals(val, getDirectVersion());
        assertEquals(val, store2.fetch());

        store2.store(val + 1);
        assertEquals(val + 1, getDirectVersion());
        assertEquals(val + 1, store.fetch());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchCorruptData() throws Exception {
        storeDirectVersion("hello");
        store.fetch();
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyData() throws Exception {
        storeDirectVersion("");
        store.fetch();
    }

    @Test(expected=StateStoreException.class)
    public void testFetchOtherFailure() throws Exception {
        when(mockCurator.fetch(NODE_PATH)).thenThrow(new Exception("hey"));
        storeWithMock.fetch();
    }

    @Test(expected=StateStoreException.class)
    public void testStoreOtherFailure() throws Exception {
        final int val = 3;
        doThrow(Exception.class)
            .when(mockCurator).store(NODE_PATH, String.valueOf(val).getBytes(CHARSET));
        storeWithMock.store(3);
    }

    private boolean directHasVersion() throws Exception {
        try {
            curator.fetch(NODE_PATH);
            return true;
        } catch (KeeperException.NoNodeException e) {
            return false;
        }
    }

    private int getDirectVersion() throws Exception {
        byte[] bytes = curator.fetch(NODE_PATH);
        String str = new String(bytes, CHARSET);
        return Integer.parseInt(str);
    }

    private void storeDirectVersion(String data) throws Exception {
        curator.store(NODE_PATH, data.getBytes(CHARSET));
    }
}
