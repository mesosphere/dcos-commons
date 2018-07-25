package com.mesosphere.sdk.state;

import com.mesosphere.sdk.config.StringConfiguration;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for {@link ConfigStore}.
 */
public class ConfigStoreTest {
    private static final String NAMESPACE = "test-namespace";
    private static final String NAMESPACE_PATH = "Services/" + NAMESPACE;

    private Persister persister;
    private ConfigStore<StringConfiguration> store;
    private StringConfiguration testConfig;

    @Before
    public void beforeEach() throws Exception {
        persister = MemPersister.newBuilder().build();
        store = new ConfigStore<StringConfiguration>(new StringConfiguration.Factory(), persister);

        testConfig = new StringConfiguration("test-config");
    }

    @Test
    public void testStoreConfig() throws Exception {
        UUID testId = store.store(testConfig);
        Assert.assertTrue(testId != null);
    }

    @Test
    public void testRootPathMapping() throws Exception {
        UUID id = store.store(testConfig);
        store.setTargetConfig(id);

        // Check that data is at root path:
        Assert.assertEquals(id.toString(), new String(persister.get("ConfigTarget"), StandardCharsets.UTF_8));
        Assert.assertNotEquals(0, persister.get("Configurations/" + id.toString()).length);

        // Check that data is NOT in namespaced path:
        checkPathNotFound(NAMESPACE_PATH + "/ConfigTarget");
        checkPathNotFound(NAMESPACE_PATH + "/Configurations/" + id.toString());

        // Check that data is accessible as expected:
        Assert.assertEquals(id, store.getTargetConfig());
        Assert.assertEquals(testConfig, store.fetch(id));
    }

    @Test
    public void testNamespacedPathMapping() throws Exception {
        store = new ConfigStore<StringConfiguration>(
                new StringConfiguration.Factory(), persister, Optional.of(NAMESPACE));
        UUID id = store.store(testConfig);
        store.setTargetConfig(id);

        // Check that data is in namespaced path:
        Assert.assertEquals(id.toString(), new String(persister.get(NAMESPACE_PATH + "/ConfigTarget"), StandardCharsets.UTF_8));
        Assert.assertNotEquals(0, persister.get(NAMESPACE_PATH + "/Configurations/" + id.toString()).length);

        // Check that data is NOT in root path:
        checkPathNotFound("ConfigTarget");
        checkPathNotFound("Configurations/" + id.toString());

        // Check that data is accessible as expected:
        Assert.assertEquals(id, store.getTargetConfig());
        Assert.assertEquals(testConfig, store.fetch(id));
    }

    @Test
    public void testStoreFetchConfig() throws Exception {
        UUID testId = store.store(testConfig);
        StringConfiguration config = store.fetch(testId);
        Assert.assertEquals(testConfig, config);
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

        Assert.assertEquals(3, ids.size());
        Assert.assertEquals(3, store.list().size());

        for (UUID id : ids) {
            Assert.assertTrue(store.list().contains(id));
        }
    }

    @Test
    public void testStoreSetTargetConfigGetTargetConfig() throws Exception {
        UUID testId = store.store(testConfig);
        store.setTargetConfig(testId);
        Assert.assertEquals(testId, store.getTargetConfig());
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

    @Test
    public void testKeyNotPresent() throws ConfigStoreException {
        Assert.assertFalse(store.hasKey(UUID.randomUUID()));
    }

    @Test
    public void testKeyIsPresent() throws ConfigStoreException {
        UUID testId = store.store(testConfig);
        Assert.assertTrue(store.hasKey(testId));
    }

    private void checkPathNotFound(String path) {
        try {
            persister.get(path);
        } catch (PersisterException e) {
            Assert.assertEquals(StorageError.Reason.NOT_FOUND, e.getReason());
        }
    }
}
