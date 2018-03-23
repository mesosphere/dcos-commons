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

public class SchemaVersionStoreTest {

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
        store = new SchemaVersionStore(persister);
        store2 = new SchemaVersionStore(persister);
        storeWithMock = new SchemaVersionStore(mockPersister);
    }

    @Test
    public void testFetchAutoInitialize() throws Exception {
        assertFalse(directHasVersion());
        store.check(3);
        assertNotEquals(0, persister.get(NODE_PATH).length);
        assertEquals(3, getDirectVersion());
    }

    @Test
    public void testStoreFetchStoreFetch() throws Exception {
        assertFalse(directHasVersion());
        final int val = 5;

        store.store(val);
        assertEquals(val, getDirectVersion());
        store.check(val);

        store.store(val + 1);
        assertEquals(val + 1, getDirectVersion());
        store.check(val + 1);
    }

    @Test
    public void testStoreFetchStoreFetch_acrossInstances() throws Exception {
        assertFalse(directHasVersion());
        final int val = 5;

        store.store(val);
        assertEquals(val, getDirectVersion());
        store2.check(val);

        store2.store(val + 1);
        assertEquals(val + 1, getDirectVersion());
        store.check(val + 1);
    }

    @Test(expected=StateStoreException.class)
    public void testCheckCorruptData() throws Exception {
        storeDirectVersion("hello");
        store.check(5);
    }

    @Test(expected=StateStoreException.class)
    public void testCheckEmptyData() throws Exception {
        storeDirectVersion("");
        store.check(3);
    }

    @Test(expected=StateStoreException.class)
    public void testCheckOtherFailure() throws Exception {
        when(mockPersister.get(NODE_PATH)).thenThrow(new PersisterException(Reason.LOGIC_ERROR, "hey"));
        storeWithMock.check(3);
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
