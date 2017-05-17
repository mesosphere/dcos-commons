package com.mesosphere.sdk.state;

import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import org.apache.curator.test.TestingServer;
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

/**
 * Tests for {@link DefaultSchemaVersionStore}.
 */
public class DefaultSchemaVersionStoreTest {
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String ROOT_ZK_PATH = "/test-root-path";
    private static final String PREFIXED_ROOT_ZK_PATH = "/dcos-service-test-root-path";
    // This value must never change. If you're changing it, you're wrong:
    private static final String NODE_PATH = PREFIXED_ROOT_ZK_PATH + "/SchemaVersion";

    private static TestingServer testZk;
    private CuratorPersister curator;
    private CuratorPersister curatorWithAcl;
    @Mock CuratorPersister mockCurator;
    private SchemaVersionStore store;
    private SchemaVersionStore storeWithAcl;
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
        curator = CuratorPersister.newBuilder(testZk.getConnectString()).build();
        curatorWithAcl = CuratorPersister.newBuilder(testZk.getConnectString())
                .setCredentials(CuratorTestUtils.USERNAME, CuratorTestUtils.PASSWORD)
                .build();
        store = new DefaultSchemaVersionStore(curator, ROOT_ZK_PATH);
        storeWithAcl = new DefaultSchemaVersionStore(curatorWithAcl, ROOT_ZK_PATH);
        store2 = new DefaultSchemaVersionStore(curator, ROOT_ZK_PATH);
        storeWithMock = new DefaultSchemaVersionStore(mockCurator, ROOT_ZK_PATH);
    }

    @Test
    public void testRootPathMapping() throws Exception {
        store.fetch();
        CuratorPersister curator = CuratorPersister.newBuilder(testZk.getConnectString()).build();
        assertNotEquals(0, curator.get("/dcos-service-test-root-path/SchemaVersion").length);
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
    public void testStoreWOAclAndFetchWithAcl() throws Exception {
        assertFalse(directWithAclHasVersion());
        final int val = 10;
        final int valUpdate = 15;

        // Store value with world:anyone then read with digest username:password.
        store.store(val);
        assertEquals(val, store.fetch());
        assertEquals(val, storeWithAcl.fetch());
        assertEquals(val, getDirectVersion());
        assertEquals(val, getWithAclDirectVersion());

        // Update the world:anyone value and should be readable with or without Auth
        store.store(valUpdate);
        assertEquals(valUpdate, store.fetch());
        assertEquals(valUpdate, storeWithAcl.fetch());
        assertEquals(valUpdate, getDirectVersion());
        assertEquals(valUpdate, getWithAclDirectVersion());

        storeWithAcl.store(valUpdate);
        assertEquals(valUpdate, store.fetch());
        assertEquals(valUpdate, storeWithAcl.fetch());
        assertEquals(valUpdate, getDirectVersion());
        assertEquals(valUpdate, getWithAclDirectVersion());
    }

    @Test
    public void testStoreWithAclAndFetchWOAcl() throws Exception {
        assertFalse(directWithAclHasVersion());
        final int val = 10;
        final int valUpdate = 15;

        // Store value with ACL.
        storeWithAcl.store(val);

        // Readable with appropriate Auth and ACL.
        assertEquals(val, storeWithAcl.fetch());
        assertEquals(val, getWithAclDirectVersion());

        // Readable with world:anyone permission.
        assertEquals(val, store.fetch());
        assertEquals(val, getDirectVersion());

        // Not writeable with world:anyone permission.
        try
        {
            storeDirectVersion(Integer.toString(valUpdate));
            fail("Should have failed with auth exception");
        }
        catch ( KeeperException.NoAuthException e )
        {
            // expected
        }

        // Not writeable with incorrect Auth
        try
        {
            CuratorPersister curatorAclSomeone = CuratorPersister.newBuilder(testZk.getConnectString())
                    .setCredentials("someone", "else")
                    .build();
            curatorAclSomeone.set(NODE_PATH, "someoneelse".getBytes(CHARSET));
            fail("Should have failed with auth exception");
        }
        catch ( KeeperException.NoAuthException e )
        {
            // expected
        }

        curatorWithAcl.deleteAll(NODE_PATH);
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
        when(mockCurator.get(NODE_PATH)).thenThrow(new Exception("hey"));
        storeWithMock.fetch();
    }

    @Test(expected=StateStoreException.class)
    public void testStoreOtherFailure() throws Exception {
        final int val = 3;
        doThrow(Exception.class)
                .when(mockCurator).set(NODE_PATH, String.valueOf(val).getBytes(CHARSET));
        storeWithMock.store(3);
    }

    private boolean directHasVersion() throws Exception {
        try {
            curator.get(NODE_PATH);
            return true;
        } catch (KeeperException.NoNodeException e) {
            return false;
        }
    }

    private boolean directWithAclHasVersion() throws Exception {
        try {
            curatorWithAcl.get(NODE_PATH);
            return true;
        } catch (KeeperException.NoNodeException e) {
            return false;
        }
    }

    private int getDirectVersion() throws Exception {
        byte[] bytes = curator.get(NODE_PATH);
        String str = new String(bytes, CHARSET);
        return Integer.parseInt(str);
    }

    private int getWithAclDirectVersion() throws Exception {
        byte[] bytes = curatorWithAcl.get(NODE_PATH);
        String str = new String(bytes, CHARSET);
        return Integer.parseInt(str);
    }

    private void storeDirectVersion(String data) throws Exception {
        curator.set(NODE_PATH, data.getBytes(CHARSET));
    }
}
