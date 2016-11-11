package org.apache.mesos.config.validate;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.*;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.mockito.Mockito.when;

public class PodSpecsCannotShrinkTest {
    private static final ConfigurationValidator<ServiceSpec> VALIDATOR = new PodSpecsCannotShrink();

    @Mock private TaskSpec taskSpec;
    @Mock private PodSpec mockPodSpec1;
    @Mock private PodSpec mockPodSpec11;
    @Mock private PodSpec mockPodSpec2;
    @Mock private PodSpec mockPodSpec22;


    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpec1.getType()).thenReturn(TestConstants.POD_TYPE + "-A1");
        when(mockPodSpec1.getTasks()).thenReturn(Arrays.asList(taskSpec));

        when(mockPodSpec11.getType()).thenReturn(TestConstants.POD_TYPE + "-A2");
        when(mockPodSpec11.getTasks()).thenReturn(Arrays.asList(taskSpec));

        when(mockPodSpec2.getType()).thenReturn(TestConstants.POD_TYPE + "-B1");
        when(mockPodSpec2.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));

        when(mockPodSpec22.getType()).thenReturn(TestConstants.POD_TYPE + "-B2");
        when(mockPodSpec22.getTasks()).thenReturn(Arrays.asList(taskSpec, taskSpec));
    }


    @Test
    public void testMatchingSize() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();
        VALIDATOR.validate(serviceSpec1, serviceSpec2);

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testPodGrowth() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }


    @Test
    public void testTaskGrowth() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec11))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }


    @Test
    public void testPodRemove() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }


    @Test
    public void testSetRename() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec22))
                .build();

        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }


    @Test
    public void testDuplicateSet() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec1))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size()); // only checked against new config
        Assert.assertEquals(2, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

}
