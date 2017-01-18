package com.mesosphere.sdk.curator;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.StringConfiguration;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tests to validate the operation of the CuratorConfigStore
 */
public class CuratorConfigStoreTest {
    private static final String ROOT_ZK_PATH = "/test-root-path";

    private static TestingServer testZk;
    private ConfigStore<StringConfiguration> store;
    private StringConfiguration testConfig;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        store = new CuratorConfigStore<StringConfiguration>(
                new StringConfiguration.Factory(), ROOT_ZK_PATH, testZk.getConnectString());

        // Check that schema version was created in the correct location:
        CuratorPersister curator = new CuratorPersister(
                testZk.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        assertNotEquals(0, curator.get("/dcos-service-test-root-path/SchemaVersion").length);

        testConfig = new StringConfiguration("test-config");
    }

    @After
    public void afterEach() {
        ((CuratorConfigStore<StringConfiguration>) store).close();
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
        CuratorPersister curator = new CuratorPersister(
                testZk.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        assertNotEquals(0, curator.get("/dcos-service-test-root-path/ConfigTarget").length);
        assertNotEquals(0, curator.get(
                "/dcos-service-test-root-path/Configurations/" + id.toString()).length);
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
