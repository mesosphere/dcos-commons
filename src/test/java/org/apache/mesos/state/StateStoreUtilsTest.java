package org.apache.mesos.state;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.testing.CuratorTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This class tests the StateStoreUtils class.
 */
public class StateStoreUtilsTest {
    private static final String ROOT_ZK_PATH = "/test-root-path";
    private static TestingServer testZk;
    private StateStore stateStore;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
    }

    @Test
    public void testEmptySuppressedProperty() {
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
    }

    @Test
    public void testSetSuppressedTrue() {
        StateStoreUtils.setSuppressed(stateStore, true);
        Assert.assertTrue(StateStoreUtils.isSuppressed(stateStore));
    }

    @Test
    public void testSetSuppressedFalse() {
        StateStoreUtils.setSuppressed(stateStore, false);
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
    }

    @Test
    public void testChangeSuppressed() {
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        StateStoreUtils.setSuppressed(stateStore, true);
        Assert.assertTrue(StateStoreUtils.isSuppressed(stateStore));
        StateStoreUtils.setSuppressed(stateStore, false);
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        StateStoreUtils.setSuppressed(stateStore, true);
        Assert.assertTrue(StateStoreUtils.isSuppressed(stateStore));
    }
}
