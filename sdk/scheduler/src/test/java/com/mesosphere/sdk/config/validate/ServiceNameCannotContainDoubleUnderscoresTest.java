package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * Tests for {@link ServiceNameCannotContainDoubleUnderscores}.
 */
public class ServiceNameCannotContainDoubleUnderscoresTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new ServiceNameCannotContainDoubleUnderscores();

    // Avoid spec validator hell by just using a mock object:
    @Mock
    private ServiceSpec mockServiceSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTripleUnderscores() throws InvalidRequirementException {
        when(mockServiceSpec.getName()).thenReturn("svc___1");
        Assert.assertEquals(1, VALIDATOR.validate(Optional.empty(), mockServiceSpec).size());
    }

    @Test
    public void testDoubleUnderscores() throws InvalidRequirementException {
        when(mockServiceSpec.getName()).thenReturn("svc__1");
        Assert.assertEquals(1, VALIDATOR.validate(Optional.empty(), mockServiceSpec).size());
    }

    @Test
    public void testSingleUnderscores() throws InvalidRequirementException {
        when(mockServiceSpec.getName()).thenReturn("svc_1");
        Assert.assertEquals(0, VALIDATOR.validate(Optional.empty(), mockServiceSpec).size());
    }

    @Test
    public void testNoUnderscores() throws InvalidRequirementException {
        when(mockServiceSpec.getName()).thenReturn("svc1");
        Assert.assertEquals(0, VALIDATOR.validate(Optional.empty(), mockServiceSpec).size());
    }
}
