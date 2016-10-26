package org.apache.mesos.config.validate;

import java.util.Arrays;

import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.specification.DefaultServiceSpecification;
import org.apache.mesos.specification.DefaultTaskSet;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskSetsCannotShrinkTest {
    private static final ConfigurationValidator<ServiceSpecification> VALIDATOR = new TaskSetsCannotShrink();

    @Mock
    private TaskSpecification mockTaskSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMatchingSize() throws InvalidRequirementException {
        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec, mockTaskSpec))));
        ServiceSpecification serviceSpec2 = new DefaultServiceSpecification(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec, mockTaskSpec))));

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testSetGrowth() throws InvalidRequirementException {
        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec))));
        ServiceSpecification serviceSpec2 = new DefaultServiceSpecification(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec, mockTaskSpec))));

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testTaskGrowth() throws InvalidRequirementException {
        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec))));
        ServiceSpecification serviceSpec2 = new DefaultServiceSpecification(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec, mockTaskSpec))));

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testSetRemove() throws InvalidRequirementException {
        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec, mockTaskSpec))));
        ServiceSpecification serviceSpec2 = new DefaultServiceSpecification(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec))));

        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testSetRename() throws InvalidRequirementException {
        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec, mockTaskSpec))));
        ServiceSpecification serviceSpec2 = new DefaultServiceSpecification(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set3", Arrays.asList(mockTaskSpec, mockTaskSpec))));

        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }

    @Test
    public void testDuplicateSet() throws InvalidRequirementException {
        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec))));
        ServiceSpecification serviceSpec2 = new DefaultServiceSpecification(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockTaskSpec)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec))));

        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec2).size()); // only checked against new config
        Assert.assertEquals(2, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec2).size());
    }
}
