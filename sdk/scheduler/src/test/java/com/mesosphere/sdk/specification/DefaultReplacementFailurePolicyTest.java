package com.mesosphere.sdk.specification;

import org.junit.Test;

import javax.validation.ConstraintViolationException;

public class DefaultReplacementFailurePolicyTest {

    @Test(expected = ConstraintViolationException.class)
    public void invalid() {
        new DefaultReplacementFailurePolicy(-1, -1);
    }
}
