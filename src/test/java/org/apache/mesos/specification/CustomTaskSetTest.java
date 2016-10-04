package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.junit.Test;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class tests the ability to customize TaskTypeSpecifications.
 */
public class CustomTaskSetTest {

    @Test
    public void testCustomCommand() {
        TaskSet taskSet = CustomTaskSet.create("custom", 3);
        Assert.assertEquals("custom 0", taskSet.getTaskSpecifications().get(0).getCommand().getValue());
        Assert.assertEquals("custom 2", taskSet.getTaskSpecifications().get(2).getCommand().getValue());

        Assert.assertEquals("custom_0", taskSet.getTaskSpecifications().get(0).getName());
        Assert.assertEquals("custom_2", taskSet.getTaskSpecifications().get(2).getName());
    }

    public static class CustomTaskSet extends DefaultTaskSet {
        public static CustomTaskSet create(String name, int count) {

            List<TaskSpecification> taskSpecifications = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                taskSpecifications.add(new DefaultTaskSpecification(
                        name + "_" + i,
                        Protos.CommandInfo.newBuilder()
                                .setValue(name + " " + i)
                                .build(),
                        Collections.emptyList(),
                        Collections.emptyList()));
            }

            return new CustomTaskSet(name, taskSpecifications);
        }

        private CustomTaskSet(String name, List<TaskSpecification> taskSpecifications) {
            super(name, taskSpecifications);
        }
    }
}
