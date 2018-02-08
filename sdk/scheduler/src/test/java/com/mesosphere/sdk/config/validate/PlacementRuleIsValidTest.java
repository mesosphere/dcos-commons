package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
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
    @Mock
    private PlacementRule placementRule;

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
    public void invalidPlacementRuleFailsValidation() throws IOException {
        when(placementRule.isValid()).thenReturn(false);
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(placementRule));
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(1));
    }

    @Test
    public void validPlacementRulePassesValidation() throws IOException {
        when(placementRule.isValid()).thenReturn(true);
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(placementRule));
        assertThat(validator.validate(Optional.empty(), serviceSpec), hasSize(0));
    }
}
