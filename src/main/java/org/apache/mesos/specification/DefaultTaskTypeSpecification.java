package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * This class provides a default implementation of the TaskTypeSpecification interface.
 */
public class DefaultTaskTypeSpecification implements TaskTypeSpecification {
    private final int count;
    private final String name;
    private final Protos.CommandInfo command;
    private final Collection<ResourceSpecification> resources;

    public DefaultTaskTypeSpecification(
            int count,
            String name,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources) {
        this.count = count;
        this.name = name;
        this.command = command;
        this.resources = resources;
    }

    @Override
    public int getCount() {
        return count;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Protos.CommandInfo getCommand() {
        return command;
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return resources;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
