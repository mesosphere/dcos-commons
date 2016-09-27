package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

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
    private final Collection<VolumeSpecification> volumes;
    private final Optional<PlacementRuleGenerator> placementOptional;

    public DefaultTaskTypeSpecification(
            int count,
            String name,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes,
            Optional<PlacementRuleGenerator> placementOptional) {
        this.count = count;
        this.name = name;
        this.command = command;
        this.resources = resources;
        this.volumes = volumes;
        this.placementOptional = placementOptional;
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
    public Protos.CommandInfo getCommand(int id) {
        return command;
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return resources;
    }

    @Override
    public Collection<VolumeSpecification> getVolumes() {
        return volumes;
    }

    @Override
    public Optional<PlacementRuleGenerator> getPlacement() {
        return placementOptional;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
