package com.mesosphere.sdk.curator;

import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testutils.TestConstants;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.apache.curator.test.TestingServer;

/**
 * Tests for {@link CuratorLocker}.
 */
public class CuratorLockerTest {
    @Mock private ServiceSpec mockServiceSpec;

    private static TestingServer testZk;
    private TestCuratorLocker locker1;
    private TestCuratorLocker locker2;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testZk);

        // easier than creating a real ServiceSpec just for two fields (zk connect string is set in tests):
        when(mockServiceSpec.getName()).thenReturn(TestConstants.SERVICE_NAME);
        when(mockServiceSpec.getZookeeperConnection()).thenReturn(testZk.getConnectString());

        locker1 = new TestCuratorLocker(mockServiceSpec);
        locker2 = new TestCuratorLocker(mockServiceSpec);
    }

    @Test
    public void testMultiAccess() throws Exception {
        locker1.lock();
        assertFalse(locker1.checkExited());
        locker2.lock();
        assertTrue(locker2.checkExited());
        locker1.unlock();

        locker2.lock();
        assertFalse(locker2.checkExited());
        locker1.lock();
        assertTrue(locker1.checkExited());
        locker2.unlock();
        locker1.lock();
        assertFalse(locker1.checkExited());
        locker1.unlock();
    }

    @Test
    public void testDoubleLockFails() throws Exception {
        locker1.lock();
        // custom exception test handling so that we clean up with an unlock() afterwards:
        boolean hadError = false;
        try {
            locker1.lock();
        } catch (IllegalStateException e) {
            hadError = true;
        }
        assertTrue(hadError);
        locker1.unlock();
    }

    @Test(expected = IllegalStateException.class)
    public void testDoubleUnlockFails() throws Exception {
        locker1.unlock();
        locker1.unlock();
    }

    private static class TestCuratorLocker extends CuratorLocker {
        private boolean exited;

        TestCuratorLocker(ServiceSpec serviceSpec) {
            super(serviceSpec);
            this.exited = false;
        }

        @Override
        protected TimeUnit getWaitTimeUnit() {
            return TimeUnit.MILLISECONDS;
        }

        @Override
        protected void exit() {
            this.exited = true;
        }

        public boolean checkExited() {
            boolean val = this.exited;
            this.exited = false;
            return val;
        }
    }
}
