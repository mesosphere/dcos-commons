package org.apache.mesos.scheduler.recovery;

import org.junit.Test;
import org.testng.Assert;

public class SimpleRecoveryPlanManagerTest {
    @Test
    public void testGetTaskNameFromRecoveryBlock() {
        Assert.assertEquals("block-0", SimpleRecoveryPlanManager.getTaskNameFromRecoveryBlock("block-0__recovery"));
    }

    @Test
    public void testGetTaskNameFromRecoveryBlockNoRecoverySuffix() {
        Assert.assertEquals("block-0", SimpleRecoveryPlanManager.getTaskNameFromRecoveryBlock("block-0"));
    }
}
