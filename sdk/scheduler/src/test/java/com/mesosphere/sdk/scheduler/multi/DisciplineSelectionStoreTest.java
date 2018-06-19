package com.mesosphere.sdk.scheduler.multi;

import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterUtils;

import org.junit.*;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link DisciplineSelectionStore}
 */
public class DisciplineSelectionStoreTest {
    private static final Set<String> SELECTED_A = new HashSet<>(Arrays.asList("foo", "/path/to/bar"));
    private static final Set<String> SELECTED_B = new HashSet<>(Arrays.asList("baz"));

    private Persister persister;
    private DisciplineSelectionStore store;

    @Before
    public void beforeEach() throws Exception {
        persister = new MemPersister();
        store = new DisciplineSelectionStore(persister);
    }

    @Test
    public void testStoreFetch() throws Exception {
        assertTrue(store.storeSelectedServices(SELECTED_A));
        assertEquals(SELECTED_A, store.fetchSelectedServices());
    }

    @Test
    public void testStoreNoChange() throws Exception {
        assertTrue(store.storeSelectedServices(SELECTED_A));
        assertFalse(store.storeSelectedServices(SELECTED_A));
        assertTrue(store.storeSelectedServices(SELECTED_B));
        assertFalse(store.storeSelectedServices(SELECTED_B));
        assertTrue(store.storeSelectedServices(Collections.emptySet()));
        assertFalse(store.storeSelectedServices(Collections.emptySet()));
    }

    @Test
    public void testStoreFetchMultiple() throws Exception {
        assertTrue(store.storeSelectedServices(SELECTED_A));
        assertEquals(SELECTED_A, store.fetchSelectedServices());
        store.storeSelectedServices(Collections.emptySet());
        assertTrue(store.fetchSelectedServices().isEmpty());
        assertTrue(store.storeSelectedServices(SELECTED_B));
        assertEquals(SELECTED_B, store.fetchSelectedServices());
        assertTrue(store.storeSelectedServices(Collections.emptySet()));
        assertTrue(store.fetchSelectedServices().isEmpty());
    }

    @Test
    public void testRootPathMapping() throws Exception {
        store.storeSelectedServices(SELECTED_A);
        assertEquals("foo__/path/to/bar", new String(persister.get("SelectedServices"), StandardCharsets.UTF_8));
    }

    @Test
    public void testFetchEmpty() throws Exception {
        assertTrue(store.fetchSelectedServices().isEmpty());
    }

    @Test
    public void testStoreClearAllData() throws Exception {
        store.storeSelectedServices(SELECTED_A);
        assertEquals(1, PersisterUtils.getAllKeys(persister).size());

        PersisterUtils.clearAllData(persister);

        // Verify nothing is left under the root.
        assertTrue(PersisterUtils.getAllKeys(persister).isEmpty());
    }

}
