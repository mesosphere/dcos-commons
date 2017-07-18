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
    @Mock
    private PodSpec mockOldPodSpec2, mockNewPodSpec2;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockOldPodSpec.getUser()).thenReturn(Optional.of(USER_A));

        when(mockOldPodSpec.getType()).thenReturn(POD_TYPE_A + "-1");
        when(mockOldPodSpec2.getType()).thenReturn(POD_TYPE_A + "-2");

        when(mockNewPodSpec.getType()).thenReturn(POD_TYPE_B + "-1");
        when(mockNewPodSpec2.getType()).thenReturn(POD_TYPE_A + "-2");
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

    @Test
    public void testOldPodSettingUserButNewPodNotSettingUser() {
        when(mockNewPodSpec.getUser()).thenReturn(Optional.empty());

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

    @Test
    public void testOldPodNotSettingUserButNewPodSettingUser() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.empty());
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

    @Test
    public void testNoUser() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.empty());
        when(mockNewPodSpec.getUser()).thenReturn(Optional.empty());

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
    public void testMultiplePodsAllSettingDifferentUsers() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.of(USER_A + "-1"));
        when(mockOldPodSpec2.getUser()).thenReturn(Optional.of(USER_A + "-2"));

        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_B + "-1"));
        when(mockNewPodSpec2.getUser()).thenReturn(Optional.of(USER_B + "-2"));

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec, mockOldPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec, mockNewPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(2, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testMultiplePodsOldSettingOneUserNewSettingMultipleUsers() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.of(USER_A + "-1"));
        when(mockOldPodSpec2.getUser()).thenReturn(Optional.of(USER_A + "-1"));

        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_B + "-1"));
        when(mockNewPodSpec2.getUser()).thenReturn(Optional.of(USER_B + "-2"));

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec, mockOldPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec, mockNewPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(2, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testMultiplePodsOldSettingMultipleUserNewSettingOneUser() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.of(USER_A + "-1"));
        when(mockOldPodSpec2.getUser()).thenReturn(Optional.of(USER_A + "-2"));

        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_B + "-1"));
        when(mockNewPodSpec2.getUser()).thenReturn(Optional.of(USER_B + "-1"));

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec, mockOldPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec, mockNewPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(2, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testMultiplePodsOldSettingNoUserNewSettingOneUser() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.empty());
        when(mockOldPodSpec2.getUser()).thenReturn(Optional.empty());

        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_B + "-1"));
        when(mockNewPodSpec2.getUser()).thenReturn(Optional.of(USER_B + "-1"));

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec, mockOldPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec, mockNewPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testMultiplePodsOldSettingNoUserNewSettingMultipleUsers() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.empty());
        when(mockOldPodSpec2.getUser()).thenReturn(Optional.empty());

        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_B + "-1"));
        when(mockNewPodSpec2.getUser()).thenReturn(Optional.of(USER_B + "-2"));

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec, mockOldPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec, mockNewPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testMultiplePodsOldSettingNoUserNewSettingNoUser() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.empty());
        when(mockOldPodSpec2.getUser()).thenReturn(Optional.empty());

        when(mockNewPodSpec.getUser()).thenReturn(Optional.empty());
        when(mockNewPodSpec2.getUser()).thenReturn(Optional.empty());

        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockOldPodSpec, mockOldPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockNewPodSpec, mockNewPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testMoreNewPodsThanOldPods() {
        when(mockOldPodSpec.getUser()).thenReturn(Optional.of(USER_A));

        when(mockNewPodSpec.getUser()).thenReturn(Optional.of(USER_A));
        when(mockNewPodSpec2.getUser()).thenReturn(Optional.of(USER_B));

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
                .pods(Arrays.asList(mockNewPodSpec, mockNewPodSpec2))
                .apiPort(TestConstants.PORT_API_VALUE)
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }
}
