package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.junit.Test;
import org.testng.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.Random;

/**
 * This class tests the ability to customize TaskTypeSpecifications.
 */
public class CustomTaskTypeSpecificationTest {

    @Test
    public void testCustomCommand() {
        Random random = new Random();
        int taskCount = random.nextInt(Integer.SIZE - 1); // Random positive integer
        TaskTypeSpecification taskTypeSpecification = new CustomTaskTypeSpecification(
                taskCount,
                "custom",
                null,
                Collections.emptyList(),
                Collections.emptyList());

        Assert.assertEquals("custom", taskTypeSpecification.getCommand().getValue());
    }

    public static class CustomTaskTypeSpecification extends DefaultTaskTypeSpecification {

        public CustomTaskTypeSpecification(
                int count,
                String name,
                Protos.CommandInfo command,
                Collection<ResourceSpecification> resources,
                Collection<VolumeSpecification> volumes) {
            super(count, name, command, resources, volumes);
        }

        @Override
        public Protos.CommandInfo getCommand() {
            return Protos.CommandInfo.newBuilder().setValue(getName()).build();
        }
    }
}
