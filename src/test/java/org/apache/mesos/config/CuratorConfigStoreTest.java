package org.apache.mesos.config;

import com.netflix.curator.test.TestingServer;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.UUID;

/**
 * Tests to validate the operation of the CuratorConfigStore
 */
public class CuratorConfigStoreTest {
    private TestingServer testZk;
    private CuratorConfigStore store;
    private String testRootZkPath = "/test-root-path";
    private String testConfig = "test-config";
    private ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);

    @Before
    public void beforeEach() throws Exception {
        testZk = new TestingServer();
        store = getTestConfigStore();
    }

    @Test
    public void testStoreConfig() throws Exception {
        UUID testId = store.store(testConfig);
        Assert.assertTrue(testId != null);
    }

    @Test
    public void testStoreFetchConfig() throws Exception {
        UUID testId = store.store(testConfig);
        String config = store.fetch(testId);
        Assert.assertEquals(testConfig, config);
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
    public void testStoreSetTargetConfigGetTargetConfig() throws Exception {
        UUID testId = store.store(testConfig);
        store.setTargetConfig(testId);
        Assert.assertEquals(testId, store.getTargetConfig());
    }

    @Test(expected=ConfigStoreException.class)
    public void testGetEmptyTargetConfig() throws Exception {
        store.getTargetConfig();
    }

    public CuratorConfigStore getTestConfigStore() {
        return new CuratorConfigStore(testRootZkPath, testZk.getConnectString(), retryPolicy);
    }
}
