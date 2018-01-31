package com.mesosphere.sdk.specification;

import org.junit.Assert;
import org.junit.Test;

public class DefaultRLimitSpecTest {
    @Test
    public void testRLimitCreationSucceeds() throws DefaultRLimitSpec.InvalidRLimitException {
        RLimitSpec rlimit = new DefaultRLimitSpec("RLIMIT_AS", 0L, 1L);

        Assert.assertEquals(rlimit.getName(), "RLIMIT_AS");
        Assert.assertEquals(rlimit.getSoft().get(), Long.valueOf(0));
        Assert.assertEquals(rlimit.getHard().get(), Long.valueOf(1));
    }

    @Test
    public void testRLimitCreationSucceedsWithUnlimitedLimits() throws DefaultRLimitSpec.InvalidRLimitException {
        RLimitSpec rlimit = new DefaultRLimitSpec("RLIMIT_AS", -1L, -1L);

        Assert.assertEquals(rlimit.getSoft().get(), Long.valueOf(-1));
        Assert.assertEquals(rlimit.getHard().get(), Long.valueOf(-1));
    }

    @Test(expected = DefaultRLimitSpec.InvalidRLimitException.class)
    public void testRLimitRequiresValidName() throws DefaultRLimitSpec.InvalidRLimitException {
        new DefaultRLimitSpec("NONSENSE", 0L, 1L);
    }

    @Test(expected = DefaultRLimitSpec.InvalidRLimitException.class)
    public void testRLimitRequiresBothLimits() throws DefaultRLimitSpec.InvalidRLimitException {
        new DefaultRLimitSpec("RLIMIT_AS", 0L, -1L);
    }

    @Test(expected = DefaultRLimitSpec.InvalidRLimitException.class)
    public void testRLimitRequiresSoftLimitLessThanHard() throws DefaultRLimitSpec.InvalidRLimitException {
        new DefaultRLimitSpec("RLIMIT_AS", 1L, 0L);
    }
}
