package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.junit.Test;
import org.testng.Assert;

import java.util.Collection;
import java.util.Collections;

/**
 * This class tests the ability to customize TaskTypeSpecifications.
 */
public class CustomTaskTypeSpecificationTest {

    @Test
    public void testCustomCommand() {
        TaskTypeSpecification taskTypeSpecification = new CustomTaskTypeSpecification(
                2,
                "custom",
                null,
                Collections.emptyList(),
                Collections.emptyList());

        Assert.assertEquals("custom 0", taskTypeSpecification.getCommand(0).getValue());
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
