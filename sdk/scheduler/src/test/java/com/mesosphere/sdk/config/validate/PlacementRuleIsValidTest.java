package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.OrRule;
import com.mesosphere.sdk.offer.evaluate.placement.TestPlacementUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class PlacementRuleIsValidTest {
    @Mock
    private ServiceSpec serviceSpec;
    @Mock
    private PodSpec podSpec;

    private static final PlacementRuleIsValid validator = new PlacementRuleIsValid();

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(serviceSpec.getPods()).thenReturn(Arrays.asList(podSpec));
        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
    }

    @Test
    public void emptyPlacementRuleIsValid() throws IOException {
        when(podSpec.getPlacementRule()).thenReturn(Optional.empty());
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(0));
    }

    @Test
    public void andRuleWithoutInvalidPlacementRuleIsValid() throws IOException {
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new AndRule(TestPlacementUtils.PASS, TestPlacementUtils.FAIL)));
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(0));
    }

    @Test
    public void andRuleWithInvalidPlacementRuleIsInvalid() throws IOException {
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new AndRule(TestPlacementUtils.INVALID, TestPlacementUtils.FAIL)));
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(1));
    }

    @Test
    public void orRuleWithoutInvalidPlacementRuleIsValid() throws IOException {
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new OrRule(TestPlacementUtils.PASS, TestPlacementUtils.FAIL)));
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(0));
    }

    @Test
    public void orRuleWithInvalidPlacementRuleIsInvalid() throws IOException {
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new OrRule(TestPlacementUtils.INVALID, TestPlacementUtils.FAIL)));
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(1));
    }

    @Test
    public void invalidPlacementRuleIsInvalid() throws IOException {
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(TestPlacementUtils.INVALID));
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(1));
    }
}
