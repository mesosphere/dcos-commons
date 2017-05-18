package com.mesosphere.sdk.state;


import org.apache.curator.test.TestingServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mesosphere.sdk.curator.CuratorPersister;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SchedulerStateTest {
    private static final String ROOT_ZK_PATH = "/test-root-path";
    private static TestingServer testZk;

    private SchedulerState schedulerState;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() {
        schedulerState = new SchedulerState(
                new DefaultStateStore(CuratorPersister.newBuilder(ROOT_ZK_PATH, testZk.getConnectString()).build()));
    }

    @Test
    public void testIsSuppressed() {
        assertFalse(schedulerState.isSuppressed());
        schedulerState.setSuppressed(true);
        assertTrue(schedulerState.isSuppressed());
        schedulerState.setSuppressed(false);
        assertFalse(schedulerState.isSuppressed());
    }
}
