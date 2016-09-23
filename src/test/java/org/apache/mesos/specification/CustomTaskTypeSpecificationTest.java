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
        if (taskCount < 0) {
            taskCount *= -1; // The above can return negative values (twos compliment bit still set?)
        }
        TaskTypeSpecification taskTypeSpecification = new CustomTaskTypeSpecification(
                taskCount,
                "custom",
                null,
                Collections.emptyList(),
                Collections.emptyList());

        int id = random.nextInt(taskCount);
        Assert.assertEquals("custom " + id, taskTypeSpecification.getCommand(id).getValue());
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
        public Protos.CommandInfo getCommand(int id) {
            return Protos.CommandInfo.newBuilder().setValue(getName() + " " + id).build();
        }
    }
}
