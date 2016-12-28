package com.mesosphere.sdk.specification;

import org.junit.Assert;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Set;

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

    @Test
    public void invalid() {
        try {
            DefaultVipSpec.newBuilder()
                    .applicationPort(-1)
                    .vipName("")
                    .vipPort(-1)
                    .build();
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertEquals(3, constraintViolations.size());
        }
    }
}
