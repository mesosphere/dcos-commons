package com.mesosphere.sdk.state;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.StringConfiguration;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tests for {@link DefaultConfigStore}.
 */
public class DefaultConfigStoreTest {
    private Persister persister;
    private ConfigStore<StringConfiguration> store;
    private StringConfiguration testConfig;

    @Before
    public void beforeEach() throws Exception {
        persister = new MemPersister();
        store = new DefaultConfigStore<StringConfiguration>(new StringConfiguration.Factory(), persister);

        // Check that schema version was created in the correct location:
        assertEquals("1", new String(persister.get("SchemaVersion"), StandardCharsets.UTF_8));

        testConfig = new StringConfiguration("test-config");
    }

    @Test
    public void testStoreConfig() throws Exception {
        UUID testId = store.store(testConfig);
        assertTrue(testId != null);
    }

    @Test
    public void testRootPathMapping() throws Exception {
        UUID id = store.store(testConfig);
        store.setTargetConfig(id);
        assertEquals(id.toString(), new String(persister.get("ConfigTarget"), StandardCharsets.UTF_8));
        assertNotEquals(0, persister.get("Configurations/" + id.toString()).length);
    }

    @Test
    public void testStoreFetchConfig() throws Exception {
        UUID testId = store.store(testConfig);
        StringConfiguration config = store.fetch(testId);
        assertEquals(testConfig, config);
    }

    @Test
    public void testRepeatedStoreConfig() throws Exception {
        store.store(testConfig);
        store.store(testConfig);
    }

    @Test(expected=ConfigStoreException.class)
    public void testStoreClearFetchConfig() throws Exception {
        UUID testId = store.store(testConfig);
        store.clear(testId);
        store.fetch(testId);
    }

    @Test
    public void testClearConfig() throws Exception {
        store.clear(UUID.randomUUID());
    }

    @Test
    public void testListConfig() throws Exception {
        Collection<UUID> ids = new ArrayList<>();
        ids.add(store.store(testConfig));
        ids.add(store.store(testConfig));
        ids.add(store.store(testConfig));

        assertEquals(3, ids.size());
        assertEquals(3, store.list().size());

        for (UUID id : ids) {
            assertTrue(store.list().contains(id));
        }
    }

    @Test
    public void testStoreSetTargetConfigGetTargetConfig() throws Exception {
        UUID testId = store.store(testConfig);
        store.setTargetConfig(testId);
        assertEquals(testId, store.getTargetConfig());
    }

    @Test
    public void testRepeatedSetTargetConfig() throws Exception {
        UUID testId = store.store(testConfig);
        store.setTargetConfig(testId);
        store.setTargetConfig(testId);
    }

    @Test(expected=ConfigStoreException.class)
    public void testGetEmptyTargetConfig() throws Exception {
        store.getTargetConfig();
    }
}
