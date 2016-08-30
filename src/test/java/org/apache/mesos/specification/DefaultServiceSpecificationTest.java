package org.apache.mesos.specification;

import org.junit.Before;
import org.junit.Test;
import org.testng.Assert;

import java.util.Arrays;
import java.util.List;

/**
 * This class tests the DefaultServiceSpecification class.
 */
public class DefaultServiceSpecificationTest {
    private List<TaskTypeSpecification> taskTypeSpecifications;
    private DefaultServiceSpecification serviceSpecification;
    private static final String SERVICE_NAME = "test-service-name";

    @Before
    public void beforeEach() {
        taskTypeSpecifications = Arrays.asList(TestTaskSpecificationFactory.getTaskTypeSpecification());
        serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                taskTypeSpecifications);
    }

    @Test
    public void testGetName() {
        Assert.assertEquals(SERVICE_NAME, serviceSpecification.getName());
    }

    @Test
    public void testGetTaskSpecifications() {
        Assert.assertEquals(1, serviceSpecification.getTaskSpecifications().size());
        Assert.assertEquals(
                taskTypeSpecifications,
                serviceSpecification.getTaskSpecifications());
    }
}
