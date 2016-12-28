package com.mesosphere.sdk.specification;

import org.junit.Assert;
import org.junit.Test;

import javax.validation.ConstraintViolationException;

public class ReplacementFailurePolicyTest {
    @Test
    public void valid() {
        ReplacementFailurePolicy object = ReplacementFailurePolicy.newBuilder()
                .minReplaceDelayMs(0)
                .permanentFailureTimoutMs(0)
                .build();

        Assert.assertNotNull(object);
    }

    @Test(expected = ConstraintViolationException.class)
    public void invalid() {
        ReplacementFailurePolicy.newBuilder()
                .minReplaceDelayMs(-1)
                .permanentFailureTimoutMs(-1)
                .build();
    }
}
