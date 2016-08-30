package org.apache.mesos.specification;

import org.junit.Test;
import org.testng.Assert;

/**
 * This class tests the DefaultTaskTypeSpecification class.
 */
public class DefaultTaskTypeSpecificationTest {

    @Test
    public void testToString() {
        Assert.assertEquals("DefaultTaskTypeSpecification{count=1, name='test-task-type', command=value: \"echo test-cmd\"\n" +
                ", resources=[DefaultResourceSpecification{name='cpus', value=type: SCALAR\n" +
                "scalar {\n" +
                "  value: 1.0\n" +
                "}\n" +
                ", role='test-role', principal='test-principal'}, DefaultResourceSpecification{name='mem', value=type: SCALAR\n" +
                "scalar {\n" +
                "  value: 1000.0\n" +
                "}\n" +
                ", role='test-role', principal='test-principal'}]}",
                TestTaskSpecificationFactory.getTaskTypeSpecification().toString());
    }
}
