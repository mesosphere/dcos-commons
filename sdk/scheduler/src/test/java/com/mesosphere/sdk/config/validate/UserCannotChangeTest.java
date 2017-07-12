package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class UserCannotChangeTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new UserCannotChange();
    private static final String USER_A = TestConstants.SERVICE_USER + "-A";
    private static final String POD_TYPE_A = TestConstants.POD_TYPE + "-A";
    private static final String USER_B = TestConstants.SERVICE_USER + "-B";
    private static final String POD_TYPE_B = TestConstants.POD_TYPE + "-B";

    @Mock
    private PodSpec mockOldPodSpec, mockNewPodSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockOldPodSpec.getUser()).thenReturn(Optional.of(USER_A));
        when(mockOldPodSpec.getType()).thenReturn(POD_TYPE_A);

        when(mockNewPodSpec.getType()).thenReturn(POD_TYPE_B);
    }

    @Test
    public void testSameUser() {
        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_A));

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testDifferentUser() {
        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_B));

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }
}
