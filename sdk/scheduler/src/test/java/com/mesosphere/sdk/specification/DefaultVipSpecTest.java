package com.mesosphere.sdk.specification;

import org.junit.Assert;
import org.junit.Test;

public class DefaultVipSpecTest {
    @Test
    public void valid() {
        DefaultVipSpec vip = DefaultVipSpec.newBuilder()
                .applicationPort(0)
                .vipName("mysvc.mesos")
                .vipPort(0)
                .build();

        Assert.assertNotNull(vip);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid() {
        DefaultVipSpec.newBuilder()
                .applicationPort(-1)
                .vipName("")
                .vipPort(-1)
                .build();
    }
}
