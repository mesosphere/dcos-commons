package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.recovery.constrain.TimedLaunchConstrainer;
import org.apache.mesos.scheduler.recovery.RecoveryType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

/**
 * This class tests the TimedLaunchConstrainer class.
 */
public class TimedLaunchConstrainerTest {
    private static final Duration MIN_DELAY = Duration.ofMillis(3000);
    private TimedLaunchConstrainer timedLaunchConstrainer;

    private static class TestTimedLaunchConstrainer extends TimedLaunchConstrainer {
        private long currentTime;

        public TestTimedLaunchConstrainer(Duration minDelay) {
            super(minDelay);
        }

        public void setCurrentTime(long currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        protected long getCurrentTimeMs() {
            return currentTime;
        }

    }

    @Before
    public void beforeEach() {
        timedLaunchConstrainer = new TimedLaunchConstrainer(MIN_DELAY);
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(timedLaunchConstrainer);
    }

    @Test
    public void testCanLaunchNoneAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.NONE));
    }

    @Test
    public void testCanLaunchTransientAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.TRANSIENT));
    }

    @Test
    public void testCanLaunchPermanentAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.PERMANENT));
    }

    @Test
    public void testCanLaunchNoneAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.NONE));
    }

    @Test
    public void testCanLaunchPermanentAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.PERMANENT));
    }

    @Test
    public void testCanLaunchTransientAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.TRANSIENT));
    }

    @Test
    public void testCanLaunchNoneAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.PERMANENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.NONE));
    }

    @Test
    public void testCanLaunchTransientAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.PERMANENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(RecoveryType.TRANSIENT));
    }

    @Test
    public void testCannotLaunchPermanentAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryType.PERMANENT);
        Assert.assertFalse(timedLaunchConstrainer.canLaunch(RecoveryType.PERMANENT));
    }

    @Test
    public void testCanLaunchAfterPermanentRecoveryAndDelay() throws InterruptedException {
        TestTimedLaunchConstrainer testTimedLaunchConstrainer = new TestTimedLaunchConstrainer(MIN_DELAY);
        testTimedLaunchConstrainer.launchHappened(null, RecoveryType.PERMANENT);
        testTimedLaunchConstrainer.setCurrentTime(System.currentTimeMillis());
        Assert.assertFalse(testTimedLaunchConstrainer.canLaunch(RecoveryType.PERMANENT));
        testTimedLaunchConstrainer.setCurrentTime(testTimedLaunchConstrainer.getCurrentTimeMs() + MIN_DELAY.toMillis() * 1000 + 1);
        Assert.assertTrue(testTimedLaunchConstrainer.canLaunch(RecoveryType.PERMANENT));
    }
}
