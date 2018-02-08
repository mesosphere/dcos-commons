package com.mesosphere.sdk.offer.evaluate.placement;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class InvalidPlacementRuleTest {

    @Test
    public void invalidPlacementRuleIsAlwaysInvalid() {
        assertThat(new InvalidPlacementRule("", "").isValid(), is(false));
    }
}
