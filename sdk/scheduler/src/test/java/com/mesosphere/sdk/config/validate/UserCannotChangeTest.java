package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.when;

public class UserCannotChangeTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new UserCannotChange();
    private static final String USER_A = TestConstants.SERVICE_USER + "-A";
    private static final String USER_B = TestConstants.SERVICE_USER + "-B";

    @Mock
    private ServiceSpec mockOldServiceSpec, mockNewServiceSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockOldServiceSpec.getUser()).thenReturn(USER_A);
    }

    @Test
    public void testSameUser() {
        when(mockNewServiceSpec.getUser()).thenReturn(USER_A);

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(mockOldServiceSpec), mockNewServiceSpec).size());
    }

    @Test
    public void testDifferentUser() {
        when(mockNewServiceSpec.getUser()).thenReturn(USER_B);

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(mockOldServiceSpec), mockNewServiceSpec).size());
    }
}
