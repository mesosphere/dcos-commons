package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.curator.test.TestingServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;

public class UninstallSchedulerDeregisterTest {

    private static TestingServer testingServer;
    private UninstallScheduler uninstallScheduler;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        StateStoreCache.resetInstanceForTests();

        StateStore stateStore = StateStoreCache.getInstance(new CuratorStateStore("testing-uninstall",
                testingServer.getConnectString()));

        // No framework ID is set yet, and there are no tasks, and no ScheduleDriver
        uninstallScheduler = new UninstallScheduler(0, Duration.ofSeconds(1), stateStore);
    }

    @Test
    public void testAllButDeregisteredPlanCompletes() throws Exception {
        assert uninstallScheduler.uninstallPlanManager.getPlan().isComplete();
    }

}