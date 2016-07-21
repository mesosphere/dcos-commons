package org.apache.mesos.config;

import org.apache.curator.test.TestingServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Tests to validate the operation of the CuratorConfigStore
 */
public class CuratorConfigStoreTest {
    private static final String ROOT_ZK_PATH = "/test-root-path";

    private TestingServer testZk;
    private ConfigStore<StringConfiguration> store;
    private StringConfiguration testConfig;
    private StringConfiguration.Factory configFactory;

    @Before
    public void beforeEach() throws Exception {
        testZk = new TestingServer();
        store = new CuratorConfigStore<StringConfiguration>(
                ROOT_ZK_PATH, testZk.getConnectString());
        testConfig = new StringConfiguration("test-config");
        configFactory = new StringConfiguration.Factory();
    }

    @Test
    public void testStoreConfig() throws Exception {
        UUID testId = store.store(testConfig);
        Assert.assertTrue(testId != null);
    }

    @Test
    public void testStoreFetchConfig() throws Exception {
        UUID testId = store.store(testConfig);
        StringConfiguration config = (StringConfiguration) store.fetch(testId, configFactory);
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
        store.fetch(testId, configFactory);
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
}
