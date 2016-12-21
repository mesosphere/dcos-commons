package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.specification.util.RLimit;
import org.junit.Assert;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public class DefaultRLimitSpecTest {
    @Test
    public void testCreateValidDefaultRLimitSpec() throws RLimit.InvalidRLimitException {
        DefaultRLimitSpec rlimits = new DefaultRLimitSpec(Arrays.asList(new RLimit("RLIMIT_AS", (long) 0, (long) 1)));
        Assert.assertEquals(rlimits.getRLimits().size(), 1);
    }

    @Test
    public void testCreateDefaultRLimitSpecFailsWithEmptyRLimitList() {
        try {
            new DefaultRLimitSpec(Collections.emptyList());
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertEquals(1, constraintViolations.size());
        }
    }

    @Test
    public void testCreateDefaultRLimitSpecFailsWithNullRLimitList() {
        try {
            new DefaultRLimitSpec(null);
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertEquals(1, constraintViolations.size());
        }
    }
}
