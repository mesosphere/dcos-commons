package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Optional;

/**
 * This class provides a default implementation of the TaskTypeSpecification interface.
 */
public class DefaultTaskTypeSpecification implements TaskTypeSpecification {
    private final int count;
    private final String name;
    private final Protos.CommandInfo command;
    private final Collection<ResourceSpecification> resources;
    private final Optional<Collection<VolumeSpecification>> volumes;

    public DefaultTaskTypeSpecification(
            int count,
            String name,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources,
            Optional<Collection<VolumeSpecification>> volumes) {
        this.count = count;
        this.name = name;
        this.command = command;
        this.resources = resources;
        this.volumes = volumes;
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
    public Optional<Collection<VolumeSpecification>> getVolumes() {
        return volumes;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
