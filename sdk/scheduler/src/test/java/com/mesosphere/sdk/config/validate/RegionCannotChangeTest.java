package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class RegionCannotChangeTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new RegionCannotChange();
    private static final String REGION_A = "A";
    private static final String REGION_B = "B";

    @Mock
    private PodSpec mockPodSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpec.getUser()).thenReturn(Optional.of(TestConstants.SERVICE_USER));
        when(mockPodSpec.getType()).thenReturn("node-1");
    }

    @Test
    public void testSameRegion() {
        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .region(REGION_A)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .region(REGION_A)
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testDifferentRegion() {
        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .region(REGION_A)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .region(REGION_B)
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testOldRegionUnset() {
        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .region(REGION_A)
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testNoRegion() {
        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }

    @Test
    public void testUpdateToRegionFromBlank() {
        ServiceSpec oldServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .build();

        ServiceSpec newServiceSpec = DefaultServiceSpec.newBuilder()
                .name("svc")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec))
                .region(REGION_A)
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(oldServiceSpec), newServiceSpec).size());
    }
}
