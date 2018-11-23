package com.mesosphere.sdk.curator;

import org.junit.*;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.testutils.TestConstants;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.test.TestingServer;

/**
 * Tests for {@link CuratorLocker}.
 */
public class CuratorLockerTest {

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

        CuratorFrameworkFactory.Builder builder = getClientBuilder(testZk.getConnectString());
        locker1 = new TestCuratorLocker(TestConstants.SERVICE_NAME, builder);
        locker2 = new TestCuratorLocker(TestConstants.SERVICE_NAME, builder);
    }

    @Test
    public void testMultiAccess() throws Exception {
        locker1.lockInternal();
        assertFalse(locker1.checkExited());
        locker2.lockInternal();
        assertTrue(locker2.checkExited());
        locker1.unlockInternal();

        locker2.lockInternal();
        assertFalse(locker2.checkExited());
        locker1.lockInternal();
        assertTrue(locker1.checkExited());
        locker2.unlockInternal();
        System.out.println("HELLO FOO");
        locker1.lockInternal();
        System.out.println("CHECKING....");
        assertFalse(locker1.checkExited());
        locker1.unlockInternal();
    }

    @Test
    public void testDoubleLockFails() throws Exception {
        locker1.lockInternal();
        // custom exception test handling so that we clean up with an unlock() afterwards:
        boolean hadError = false;
        try {
            locker1.lockInternal();
        } catch (IllegalStateException e) {
            hadError = true;
        }
        assertTrue(hadError);
        locker1.unlockInternal();
    }

    @Test(expected = IllegalStateException.class)
    public void testDoubleUnlockFails() throws Exception {
        locker1.unlockInternal();
        locker1.unlockInternal();
    }

    @Test
    public void testStaticDoubleLockFails() throws Exception {
        CuratorFrameworkFactory.Builder builder = getClientBuilder(testZk.getConnectString());

        CuratorLocker.lock(TestConstants.SERVICE_NAME, builder);
        // custom exception test handling so that we clean up with an unlock() afterwards:
        boolean hadError = false;
        try {
            CuratorLocker.lock(TestConstants.SERVICE_NAME, builder);
        } catch (IllegalStateException e) {
            hadError = true;
        }
        assertTrue(hadError);
        CuratorLocker.unlock();
    }

    public void testStaticDoubleUnlockNoop() throws Exception {
        CuratorLocker.unlock();
        CuratorLocker.unlock();
    }

    private static CuratorFrameworkFactory.Builder getClientBuilder(String connectString) {
        return CuratorFrameworkFactory.builder()
                .connectString(testZk.getConnectString())
                .retryPolicy(CuratorUtils.getDefaultRetry());
    }

    private static class TestCuratorLocker extends CuratorLocker {
        private boolean exited;

        TestCuratorLocker(String serviceName, CuratorFrameworkFactory.Builder clientBuilder) {
            super(serviceName, clientBuilder);
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
