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
    private PodSpec mockPodSpecA1;
    @Mock
    private PodSpec mockPodSpecA1WithHigherCount;
    @Mock
    private PodSpec mockPodSpecB1;
    @Mock
    private PodSpec mockPodSpecB2;
    @Mock
    private ServiceSpec mockDuplicatePodServiceSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpecA1.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpecA1.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpecA1.getCount()).thenReturn(1);

        when(mockPodSpecA1WithHigherCount.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpecA1WithHigherCount.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(mockPodSpecA1WithHigherCount.getCount()).thenReturn(100);

        when(mockPodSpecB1.getType()).thenReturn(TestConstants.POD_TYPE + "-B1");
        when(mockPodSpecB1.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));
        when(mockPodSpecB1.getCount()).thenReturn(1);

        when(mockPodSpecB2.getType()).thenReturn(TestConstants.POD_TYPE + "-B2");
        when(mockPodSpecB2.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));
        when(mockPodSpecB2.getCount()).thenReturn(1);
    }

    @Test
    public void testMatchingSizeIsValid() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = getServiceSpec(mockPodSpecA1, mockPodSpecB1);
        ServiceSpec serviceSpec2 = getServiceSpec("svc2", mockPodSpecA1, mockPodSpecB1);
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testPodAddIsValid() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = getServiceSpec(mockPodSpecA1);
        ServiceSpec serviceSpec2 = getServiceSpec("svc2", mockPodSpecA1, mockPodSpecB1);
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testPodRemoveIsInvalid() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = getServiceSpec(mockPodSpecA1, mockPodSpecB1);
        ServiceSpec serviceSpec2 = getServiceSpec("svc2", mockPodSpecA1);
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testAllowedPodRemoveIsValid() throws InvalidRequirementException {
        when(mockPodSpecB1.getAllowDecommission()).thenReturn(true);
        ServiceSpec serviceSpec1 = getServiceSpec(mockPodSpecA1, mockPodSpecB1);
        ServiceSpec serviceSpec2 = getServiceSpec("svc2", mockPodSpecA1);
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testAllowedPodRenameIsValid() throws InvalidRequirementException {
        when(mockPodSpecB1.getAllowDecommission()).thenReturn(true);
        ServiceSpec serviceSpec1 = getServiceSpec(mockPodSpecA1, mockPodSpecB1);
        ServiceSpec serviceSpec2 = getServiceSpec("svc2", mockPodSpecA1, mockPodSpecB2);
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        // Still fails in the other direction since B2 doesn't have the decommission bit set:
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testPodRenameIsInvalid() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = getServiceSpec(mockPodSpecA1, mockPodSpecB1);
        ServiceSpec serviceSpec2 = getServiceSpec("svc2", mockPodSpecA1, mockPodSpecB2);
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testIdenticalSpecIsValid() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = getServiceSpec(mockPodSpecA1);
        ServiceSpec serviceSpec2 = getServiceSpec(mockPodSpecA1);
        // only checked against new config
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).size());
    }

    @Test
    public void testReducingPodCountIsInvalid() {
        final ServiceSpec serviceWithManyPods = getServiceSpec(mockPodSpecA1WithHigherCount);
        final ServiceSpec serviceWithFewPods = getServiceSpec(mockPodSpecA1);
        assertThat(VALIDATOR.validate(Optional.of(serviceWithManyPods), serviceWithFewPods), hasSize(1));
    }

    @Test
    public void testReducingSourceAllowedPodCountIsInvalid() {
        when(mockPodSpecA1WithHigherCount.getAllowDecommission()).thenReturn(true);
        final ServiceSpec serviceWithManyPods = getServiceSpec(mockPodSpecA1WithHigherCount);
        final ServiceSpec serviceWithFewPods = getServiceSpec(mockPodSpecA1);
        assertThat(VALIDATOR.validate(Optional.of(serviceWithManyPods), serviceWithFewPods), hasSize(1));
    }

    @Test
    public void testReducingDestinationAllowedPodCountIsValid() {
        when(mockPodSpecA1.getAllowDecommission()).thenReturn(true);
        final ServiceSpec serviceWithManyPods = getServiceSpec(mockPodSpecA1WithHigherCount);
        final ServiceSpec serviceWithFewPods = getServiceSpec(mockPodSpecA1);
        assertThat(VALIDATOR.validate(Optional.of(serviceWithManyPods), serviceWithFewPods), hasSize(0));
    }

    @Test
    public void testReducingBothAllowedPodCountIsValid() {
        when(mockPodSpecA1.getAllowDecommission()).thenReturn(true);
        when(mockPodSpecA1WithHigherCount.getAllowDecommission()).thenReturn(true);
        final ServiceSpec serviceWithManyPods = getServiceSpec(mockPodSpecA1WithHigherCount);
        final ServiceSpec serviceWithFewPods = getServiceSpec(mockPodSpecA1);
        assertThat(VALIDATOR.validate(Optional.of(serviceWithManyPods), serviceWithFewPods), hasSize(0));
    }

    @Test
    public void testIncreasingPodCountIsValid() {
        final ServiceSpec serviceWithManyPods = getServiceSpec(mockPodSpecA1WithHigherCount);
        final ServiceSpec serviceWithFewPods = getServiceSpec(mockPodSpecA1);
        assertThat(VALIDATOR.validate(Optional.of(serviceWithFewPods), serviceWithManyPods), is(empty()));
    }

    @Test
    public void testDuplicatePodTypesAreInvalid() {
        when(mockDuplicatePodServiceSpec.getPods()).thenReturn(Arrays.asList(mockPodSpecA1, mockPodSpecA1));
        assertThat(VALIDATOR.validate(Optional.of(mockDuplicatePodServiceSpec), mockDuplicatePodServiceSpec), hasSize(1));
    }

    @Test
    public void testOldConfigNotPresentIsValid() {
        assertThat(VALIDATOR.validate(Optional.empty(), null), is(empty()));
    }

    private static ServiceSpec getServiceSpec(PodSpec... podSpecs) {
        return getServiceSpec("svc1", podSpecs);
    }

    private static ServiceSpec getServiceSpec(String serviceName, PodSpec... podSpecs) {
        return DefaultServiceSpec.newBuilder()
                .name(serviceName)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(podSpecs))
                .build();
    }
}
