package com.mesosphere.sdk.specification;

import org.junit.Assert;
import org.junit.Test;

public class RLimitSpecTest {
    @Test
    public void testRLimitCreationSucceeds() throws RLimitSpec.InvalidRLimitException {
        RLimitSpec rlimit = new RLimitSpec("RLIMIT_AS", 0L, 1L);

        Assert.assertEquals(rlimit.getName(), "RLIMIT_AS");
        Assert.assertEquals(rlimit.getSoft().get(), Long.valueOf(0));
        Assert.assertEquals(rlimit.getHard().get(), Long.valueOf(1));
    }

    @Test
    public void testRLimitCreationSucceedsWithUnlimitedLimits() throws RLimitSpec.InvalidRLimitException {
        RLimitSpec rlimit = new RLimitSpec("RLIMIT_AS", -1L, -1L);

        Assert.assertEquals(rlimit.getSoft().get(), Long.valueOf(-1));
        Assert.assertEquals(rlimit.getHard().get(), Long.valueOf(-1));
    }

    @Test(expected = RLimitSpec.InvalidRLimitException.class)
    public void testRLimitRequiresValidName() throws RLimitSpec.InvalidRLimitException {
        new RLimitSpec("NONSENSE", 0L, 1L);
    }

    @Test(expected = RLimitSpec.InvalidRLimitException.class)
    public void testRLimitRequiresBothLimits() throws RLimitSpec.InvalidRLimitException {
        new RLimitSpec("RLIMIT_AS", 0L, -1L);
    }

    @Test(expected = RLimitSpec.InvalidRLimitException.class)
    public void testRLimitRequiresSoftLimitLessThanHard() throws RLimitSpec.InvalidRLimitException {
        new RLimitSpec("RLIMIT_AS", 1L, 0L);
    }
}
