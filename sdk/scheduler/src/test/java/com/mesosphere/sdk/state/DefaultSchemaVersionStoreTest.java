package com.mesosphere.sdk.state;

import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class DefaultSchemaVersionStoreTest {

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    // This value must never change. If you're changing it, you're wrong:
    private static final String NODE_PATH = "SchemaVersion";

    private Persister persister;
    @Mock Persister mockPersister;
    private SchemaVersionStore store;
    private SchemaVersionStore store2;
    private SchemaVersionStore storeWithMock;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        persister = new MemPersister();
        store = new DefaultSchemaVersionStore(persister);
        store2 = new DefaultSchemaVersionStore(persister);
        storeWithMock = new DefaultSchemaVersionStore(mockPersister);
    }

    @Test
    public void testRootPathMapping() throws Exception {
        store.fetch();
        assertNotEquals(0, persister.get(NODE_PATH).length);
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
        when(mockPersister.get(NODE_PATH)).thenThrow(new PersisterException(Reason.LOGIC_ERROR, "hey"));
        storeWithMock.fetch();
    }

    @Test(expected=StateStoreException.class)
    public void testStoreOtherFailure() throws Exception {
        final int val = 3;
        doThrow(Exception.class).when(mockPersister).set(NODE_PATH, String.valueOf(val).getBytes(CHARSET));
        storeWithMock.store(3);
    }

    private boolean directHasVersion() throws Exception {
        try {
            persister.get(NODE_PATH);
            return true;
        } catch (PersisterException e) {
            return false;
        }
    }

    private int getDirectVersion() throws Exception {
        return Integer.parseInt(new String(persister.get(NODE_PATH), CHARSET));
    }

    private void storeDirectVersion(String data) throws Exception {
        persister.set(NODE_PATH, data.getBytes(CHARSET));
    }
}
