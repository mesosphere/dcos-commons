package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * This class tests the ability to customize TaskTypeSpecifications.
 */
public class CustomPodSetTest {
    private static final int taskCount = 3;

    @Test
    public void testCustomCommand() {
        PodSetSpecification podSet = CustomPodSet.create("custom", taskCount);

        for (int i = taskCount; i < taskCount; ++i) {
            Assert.assertEquals(false, podSet.getTaskSpecifications().get(i).getContainer().isPresent());
            Assert.assertEquals(true, podSet.getTaskSpecifications().get(i).getCommand().isPresent());
            Assert.assertEquals(TestConstants.TASK_TYPE, podSet.getTaskSpecifications().get(i).getType());
            Assert.assertEquals(String.format("custom_%s", i), podSet.getTaskSpecifications().get(i).getName());
            Assert.assertEquals(String.format("custom %s", i), podSet.getTaskSpecifications().get(i).getCommand().get().getValue());
        }
    }

    @Test
    public void testCustomContainer() {
        PodSetSpecification podSet = CustomPodSet.create("custom", taskCount, TestConstants.CONTAINER_INFO);
        for (int i = taskCount; i < taskCount; ++i) {
            Assert.assertEquals(true, podSet.getTaskSpecifications().get(i).getContainer().isPresent());
            Assert.assertEquals(false, podSet.getTaskSpecifications().get(i).getCommand().isPresent());
            Assert.assertEquals(TestConstants.TASK_TYPE, podSet.getTaskSpecifications().get(i).getType());
            Assert.assertEquals(String.format("custom_%s", i), podSet.getTaskSpecifications().get(i).getName());
            Assert.assertEquals(TestConstants.CONTAINER_INFO.getDocker().getImage(), podSet.getTaskSpecifications().get(i).getContainer().get().getDocker().getImage());
            Assert.assertEquals(TestConstants.CONTAINER_INFO.getDocker().getNetwork(), podSet.getTaskSpecifications().get(i).getContainer().get().getDocker().getNetwork());
        }
    }

    @Test
    public void testCustomContainerCommand() {
        PodSetSpecification podSet = CustomPodSet.create("custom", taskCount, TestConstants.CONTAINER_INFO, TestConstants.COMMAND_INFO);
        for (int i = 0; i < taskCount; ++i) {
            Assert.assertEquals(true, podSet.getTaskSpecifications().get(i).getContainer().isPresent());
            Assert.assertEquals(true, podSet.getTaskSpecifications().get(i).getCommand().isPresent());
            Assert.assertEquals(TestConstants.TASK_TYPE, podSet.getTaskSpecifications().get(i).getType());
            Assert.assertEquals(String.format("custom_%s", i), podSet.getTaskSpecifications().get(i).getName());
            Assert.assertEquals(TestConstants.COMMAND_INFO.getValue(), podSet.getTaskSpecifications().get(i).getCommand().get().getValue());
            Assert.assertEquals(TestConstants.CONTAINER_INFO.getDocker().getImage(), podSet.getTaskSpecifications().get(i).getContainer().get().getDocker().getImage());
            Assert.assertEquals(TestConstants.CONTAINER_INFO.getDocker().getNetwork(), podSet.getTaskSpecifications().get(i).getContainer().get().getDocker().getNetwork());
        }
    }

    public static class CustomPodSet extends DefaultPodSetSpecification {
        public static CustomPodSet create(String name, int count) {

            List<TaskSpecification> taskSpecifications = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                taskSpecifications.add(new DefaultTaskSpecification(
                        name + "_" + i,
                        TestConstants.TASK_TYPE,
                        Protos.CommandInfo.newBuilder()
                                .setValue(name + " " + i)
                                .build(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Optional.empty(),
                        Optional.empty()));
            }

            return new CustomPodSet(name, taskSpecifications);
        }

        public static CustomPodSet create(String name, int count, Protos.ContainerInfo container) {

            List<TaskSpecification> taskSpecifications = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                taskSpecifications.add(new DefaultTaskSpecification(
                        name + "_" + i,
                        TestConstants.TASK_TYPE,
                        container,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Optional.empty(),
                        Optional.empty()));
            }

            return new CustomPodSet(name, taskSpecifications);
        }

        public static CustomPodSet create(String name, int count, Protos.ContainerInfo container, Protos.CommandInfo command) {

            List<TaskSpecification> taskSpecifications = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                taskSpecifications.add(new DefaultTaskSpecification(
                        name + "_" + i,
                        TestConstants.TASK_TYPE,
                        container,
                        command,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Optional.empty(),
                        Optional.empty()));
            }

            return new CustomPodSet(name, taskSpecifications);
        }

        private CustomPodSet(String name, List<TaskSpecification> taskSpecifications) {
            super(name, taskSpecifications);
        }
    }
}
