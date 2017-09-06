package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.InvalidRequirementException;
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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class PodSpecsCannotShrinkTest {
    private static final ConfigValidator<ServiceSpec> VALIDATOR = new PodSpecsCannotShrink();

    @Mock
    private TaskSpec taskSpec;
    @Mock
    private PodSpec mockPodSpec1;
    @Mock
    private PodSpec mockPodSpec1WithHigherCount;
    @Mock
    private PodSpec mockPodSpec11;
    @Mock
    private PodSpec mockPodSpec2;
    @Mock
    private PodSpec mockPodSpec22;
    @Mock
    private ServiceSpec mockDuplicatePodServiceSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpec1.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpec1.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpec1.getCount()).thenReturn(1);


        when(mockPodSpec1WithHigherCount.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpec1WithHigherCount.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpec1WithHigherCount.getCount()).thenReturn(100);

        when(mockPodSpec11.getType()).thenReturn(TestConstants.POD_TYPE + "-A2");
        when(mockPodSpec11.getTasks()).thenReturn(Arrays.asList(taskSpec));

        when(mockPodSpec2.getType()).thenReturn(TestConstants.POD_TYPE + "-B1");
        when(mockPodSpec2.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));

        when(mockPodSpec22.getType()).thenReturn(TestConstants.POD_TYPE + "-B2");
        when(mockPodSpec22.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));
    }


    @Test
    public void testMatchingSize() throws InvalidRequirementException {
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
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testPodGrowth() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testTaskGrowth() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec11))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testPodRemove() throws InvalidRequirementException {
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

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }


    @Test
    public void testSetRename() throws InvalidRequirementException {
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
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec22))
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }


    @Test
    public void testDuplicateSet() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        // only checked against new config
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testReducingPodCountIsInvalid() {
        final ServiceSpec serviceWithManyPods = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1WithHigherCount))
                .build();

        final ServiceSpec serviceWithFewPods = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        assertThat(VALIDATOR.validate(Optional.of(serviceWithManyPods), serviceWithFewPods), hasSize(1));
    }

    @Test
    public void testIncreasingPodCountIsValid() {
        final ServiceSpec serviceWithManyPods = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1WithHigherCount))
                .build();

        final ServiceSpec serviceWithFewPods = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        assertThat(VALIDATOR.validate(Optional.of(serviceWithFewPods), serviceWithManyPods), is(empty()));
    }

    @Test
    public void testDuplicatePodTypesAreInvalid() {
        when(mockDuplicatePodServiceSpec.getPods()).thenReturn(Arrays.asList(mockPodSpec1, mockPodSpec1));

        assertThat(VALIDATOR.validate(Optional.of(mockDuplicatePodServiceSpec), mockDuplicatePodServiceSpec), hasSize(1));

    }

    @Test
    public void testOldConfigNotPresentIsValid() {
        assertThat(VALIDATOR.validate(Optional.empty(), null), is(empty()));
    }
}
