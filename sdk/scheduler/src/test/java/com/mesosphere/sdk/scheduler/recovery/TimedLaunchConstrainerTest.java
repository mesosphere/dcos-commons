package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.recovery.constrain.TimedLaunchConstrainer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

/**
 * This class tests the TimedLaunchConstrainer class.
 */
public class TimedLaunchConstrainerTest {
    private static final Duration MIN_DELAY = Duration.ofMillis(3000);
    private static final RecoveryRequirement NO_RECOVERY_REQUIREMENT = new DefaultRecoveryRequirement(null, RecoveryRequirement.RecoveryType.NONE, null);
    private static final RecoveryRequirement TRANSIENT_RECOVERY_REQUIREMENT = new DefaultRecoveryRequirement(null, RecoveryRequirement.RecoveryType.TRANSIENT, null);
    private static final RecoveryRequirement PERMANENT_RECOVERY_REQUIREMENT = new DefaultRecoveryRequirement(null, RecoveryRequirement.RecoveryType.PERMANENT, null);
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
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(NO_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchTransientAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(TRANSIENT_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchPermanentAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(PERMANENT_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchNoneAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(NO_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchPermanentAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(PERMANENT_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchTransientAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(TRANSIENT_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchNoneAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.PERMANENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(NO_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchTransientAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.PERMANENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(TRANSIENT_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCannotLaunchPermanentAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.PERMANENT);
        Assert.assertFalse(timedLaunchConstrainer.canLaunch(PERMANENT_RECOVERY_REQUIREMENT));
    }

    @Test
    public void testCanLaunchAfterPermanentRecoveryAndDelay() throws InterruptedException {
        TestTimedLaunchConstrainer testTimedLaunchConstrainer = new TestTimedLaunchConstrainer(MIN_DELAY);
        testTimedLaunchConstrainer.launchHappened(null, RecoveryRequirement.RecoveryType.PERMANENT);
        testTimedLaunchConstrainer.setCurrentTime(System.currentTimeMillis());
        Assert.assertFalse(testTimedLaunchConstrainer.canLaunch(PERMANENT_RECOVERY_REQUIREMENT));
        testTimedLaunchConstrainer.setCurrentTime(testTimedLaunchConstrainer.getCurrentTimeMs() + MIN_DELAY.toMillis() * 1000 + 1);
        Assert.assertTrue(testTimedLaunchConstrainer.canLaunch(PERMANENT_RECOVERY_REQUIREMENT));
    }
}
