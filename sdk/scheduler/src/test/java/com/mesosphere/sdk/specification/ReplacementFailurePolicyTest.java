package com.mesosphere.sdk.specification;

import org.junit.Assert;
import org.junit.Test;

public class ReplacementFailurePolicyTest {
    @Test
    public void valid() {
        ReplacementFailurePolicy object = ReplacementFailurePolicy.newBuilder()
                .minReplaceDelaySecs(0)
                .permanentFailureTimoutSecs(0)
                .build();

        Assert.assertNotNull(object);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid() {
        ReplacementFailurePolicy.newBuilder()
                .minReplaceDelaySecs(-1)
                .permanentFailureTimoutSecs(-1)
                .build();
    }
}
