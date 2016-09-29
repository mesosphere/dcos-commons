package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class provides a default implementation of the TaskSet interface.
 */
public class DefaultTaskSet implements TaskSet {
    private final String name;
    private final List<TaskSpecification> taskSpecifications;

    public static DefaultTaskSet create(
            int count,
            String name,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes) {

        List<TaskSpecification> taskSpecifications = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            taskSpecifications.add(new DefaultTaskSpecification(name + "-" + i, command, resources, volumes));
        }

        return new DefaultTaskSet(name, taskSpecifications);
    }

    public static DefaultTaskSet create(String name, List<TaskSpecification> taskSpecifications) {
        return new DefaultTaskSet(name, taskSpecifications);
    }

    protected DefaultTaskSet(String name, List<TaskSpecification> taskSpecifications) {
        this.name = name;
        this.taskSpecifications = taskSpecifications;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<TaskSpecification> getTaskSpecifications() {
        return taskSpecifications;
    }


    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
