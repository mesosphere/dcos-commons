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

public class PodSpecsCannotShrinkTest {
    private static final ConfigurationValidator<ServiceSpec> VALIDATOR = new PodSpecsCannotShrink();

    @Mock private PodSpec mockPodSpec1;
    @Mock private PodSpec mockPodSpec2;


    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
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

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testAddPod() throws InvalidRequirementException {
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

    /*
    @Test
    public void testTaskGrowth() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = new DefaultServiceSpec(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockPodSpec))));
        ServiceSpec serviceSpec2 = new DefaultServiceSpec(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockPodSpec, mockPodSpec))));

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }
    */

    @Test
    public void testRemovePod() throws InvalidRequirementException {
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

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    /*
    @Test
    public void testSetRename() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = new DefaultServiceSpec(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockPodSpec, mockPodSpec))));
        ServiceSpec serviceSpec2 = new DefaultServiceSpec(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec)),
                        DefaultTaskSet.create("set3", Arrays.asList(mockPodSpec, mockPodSpec))));

        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testDuplicateSet() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = new DefaultServiceSpec(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec)),
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec))));
        ServiceSpec serviceSpec2 = new DefaultServiceSpec(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockPodSpec))));

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size()); // only checked against new config
        Assert.assertEquals(2, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }
    */
}
