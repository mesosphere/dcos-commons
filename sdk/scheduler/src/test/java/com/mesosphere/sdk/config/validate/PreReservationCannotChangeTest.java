package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class PreReservationCannotChangeTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new PreReservationCannotChange();

    @Mock
    private TaskSpec taskSpec;
    @Mock
    private PodSpec mockPodSpec1;
    @Mock
    private PodSpec mockPodSpec2;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpec1.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpec1.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpec1.getPreReservedRole()).thenReturn("pre-reserved-role");

        when(mockPodSpec2.getType()).thenReturn(TestConstants.POD_TYPE + "-B1");
        when(mockPodSpec2.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));
        when(mockPodSpec2.getPreReservedRole()).thenReturn("pre-reserved-role");

    }

    @Test
    public void testPodRemoval() {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());


    }
}
