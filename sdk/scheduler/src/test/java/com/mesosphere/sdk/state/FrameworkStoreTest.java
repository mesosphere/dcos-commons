package com.mesosphere.sdk.state;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterUtils;

import org.junit.*;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;

/**
 * Tests to validate the operation of the {@link FrameworkStore}.
 */
public class FrameworkStoreTest {
    private static final Protos.FrameworkID FRAMEWORK_ID =
            Protos.FrameworkID.newBuilder().setValue("test-framework-id").build();

    private Persister persister;
    private FrameworkStore store;

    @Before
    public void beforeEach() throws Exception {
        persister = new MemPersister();
        store = new FrameworkStore(persister);

        // Check that schema version was created in the correct location:
        assertEquals("1", new String(persister.get("SchemaVersion"), StandardCharsets.UTF_8));
    }

    @Test
    public void testStoreFetchFrameworkId() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        assertEquals(FRAMEWORK_ID, store.fetchFrameworkId().get());
    }

    @Test
    public void testRootPathMapping() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        assertArrayEquals(FRAMEWORK_ID.toByteArray(), persister.get("FrameworkID"));
    }

    @Test
    public void testFetchEmptyFrameworkId() throws Exception {
        assertFalse(store.fetchFrameworkId().isPresent());
    }

    @Test
    public void testStoreClearFrameworkId() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        store.clearFrameworkId();
    }

    @Test
    public void testStoreClearFetchFrameworkId() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        store.clearFrameworkId();
        assertFalse(store.fetchFrameworkId().isPresent());
    }

    @Test
    public void testClearEmptyFrameworkId() throws Exception {
        store.clearFrameworkId();
    }

    @Test
    public void testStoreClearAllData() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        // Schema version + framework id itself:
        assertEquals(2, PersisterUtils.getAllKeys(persister).size());

        PersisterUtils.clearAllData(store.getPersister());

        // Verify nothing is left under the root.
        assertTrue(PersisterUtils.getAllKeys(persister).isEmpty());
    }

}
