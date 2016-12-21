package com.mesosphere.sdk.specification.util;

import org.junit.Assert;
import org.junit.Test;

public class RLimitTest {
    @Test
    public void testRLimitCreationSucceeds() throws RLimit.InvalidRLimitException {
        RLimit rlimit = new RLimit("RLIMIT_AS", 0L, 1L);

        Assert.assertEquals(rlimit.getName(), "RLIMIT_AS");
        Assert.assertEquals(rlimit.getSoft().get(), Long.valueOf(0));
        Assert.assertEquals(rlimit.getHard().get(), Long.valueOf(1));
    }

    @Test
    public void testRLimitCreationSucceedsWithUnlimitedLimits() throws RLimit.InvalidRLimitException {
        RLimit rlimit = new RLimit("RLIMIT_AS", -1L, -1L);

        Assert.assertEquals(rlimit.getSoft().get(), Long.valueOf(-1));
        Assert.assertEquals(rlimit.getHard().get(), Long.valueOf(-1));
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void testRLimitRequiresValidName() throws RLimit.InvalidRLimitException {
        new RLimit("NONSENSE", 0L, 1L);
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void testRLimitRequiresBothLimits() throws RLimit.InvalidRLimitException {
        new RLimit("RLIMIT_AS", 0L, -1L);
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void testRLimitRequiresSoftLimitLessThanHard() throws RLimit.InvalidRLimitException {
        new RLimit("RLIMIT_AS", 1L, 0L);
    }
}
