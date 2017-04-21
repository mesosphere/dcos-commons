package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by gabriel on 3/28/17.
 */
public class SchedulerUtilsTest {
    private static final String FRAMEWORK_NAME = "framework";

    @Test
    public void testNameToRoleSimple() {
        Assert.assertEquals("framework546173438-role", SchedulerUtils.nameToRole(FRAMEWORK_NAME));
    }

    @Test
    public void testNameToRoleNested() {
        Assert.assertEquals("framework854300255-role", SchedulerUtils.nameToRole("/group/" + FRAMEWORK_NAME));
    }
}
