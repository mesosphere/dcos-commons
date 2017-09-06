package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;


public class PreReservationCannotChangeTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new PreReservationCannotChange();

    @Mock
    private TaskSpec taskSpec;
    @Mock
    private PodSpec mockPodSpec1;
    @Mock
    private PodSpec mockPodSpec1WithNoRole;
    @Mock
    private PodSpec mockPodSpec1WithDifferentRole;
    @Mock
    private PodSpec mockPodSpec2;
    @Mock
    private PodSpec mockPodSpec2WithDifferentRole;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpec1.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpec1.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpec1.getPreReservedRole()).thenReturn("pre-reserved-role");

        when(mockPodSpec1WithNoRole.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpec1WithNoRole.getTasks()).thenReturn(Arrays.asList(taskSpec));


        when(mockPodSpec1WithDifferentRole.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpec1WithDifferentRole.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpec1WithDifferentRole.getPreReservedRole()).thenReturn("pre-reserved-role-plus-difference");


        when(mockPodSpec2.getType()).thenReturn(TestConstants.POD_TYPE + "-B1");
        when(mockPodSpec2.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));
        when(mockPodSpec2.getPreReservedRole()).thenReturn("pre-reserved-role");

        when(mockPodSpec2WithDifferentRole.getType()).thenReturn(TestConstants.POD_TYPE + "-B1");
        when(mockPodSpec2WithDifferentRole.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpec2WithDifferentRole.getPreReservedRole()).thenReturn("pre-reserved-role-plus-difference");
    }

    @Test
    public void testNoInitialPodPassesValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.empty();

        assertThat(VALIDATOR.validate(serviceSpec1, null), is(empty()));
    }


    @Test
    public void testMatchingServiceSpecsPassesValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build());
        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec1.get()), is(empty()));
    }

    @Test
    public void testSwappedPodsDoesPassesValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build());


        final ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec2, mockPodSpec1))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), is(empty()));
    }

    @Test
    public void testReplacedPodPassesValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build());


        final ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), is(empty()));
    }

    @Test
    public void testFirstPodChangesReservedRoleFailsValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build());


        final ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1WithDifferentRole, mockPodSpec2))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), hasSize(1));
    }

    @Test
    public void testSecondPodChangesReservedRoleFailsValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build());


        final ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2WithDifferentRole))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), hasSize(1));
    }


    @Test
    public void testFirstPodRemovalPassesValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build());

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec2))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), is(empty()));
    }

    @Test
    public void testSecondPodRemovalPassesValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build());

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), is(empty()));
    }


    @Test
    public void testRemovalOfPreReservedRoleFailsValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build());
        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1WithNoRole))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), hasSize(1));

    }

    @Test
    public void testAdditionOfPreReservedRoleFailsValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1WithNoRole))
                .build());
        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), hasSize(1));

    }

    @Test
    public void testMissingPreReservedRolesPassesValidation() {
        Optional<ServiceSpec> serviceSpec1 = Optional.of(DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1WithNoRole))
                .build());
        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1WithNoRole))
                .build();

        assertThat(VALIDATOR.validate(serviceSpec1, serviceSpec2), is(empty()));

    }

}
