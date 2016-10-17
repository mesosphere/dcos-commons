package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * This implementation of the TaskSpecification is for test purposes.  It allows what would otherwise be bad practices
 * like changing the ResourceSpecifications encapsulated by the TaskSpecification after construction.
 */
public class TestTaskSpecification implements TaskSpecification {
    private final String name;
    private final String type;
    private final Protos.CommandInfo command;
    private final Collection<VolumeSpecification> volumes;
    private Collection<ResourceSpecification> resources;
    private Optional<PlacementRuleGenerator> placement;

    public TestTaskSpecification(TaskSpecification taskSpecification) {
        this.name = taskSpecification.getName();
        this.type = taskSpecification.getType();
        this.command = taskSpecification.getCommand();
        this.resources = taskSpecification.getResources();
        this.volumes = taskSpecification.getVolumes();
        this.placement = taskSpecification.getPlacement();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
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
    public Collection<VolumeSpecification> getVolumes() {
        return volumes;
    }

    @Override
    public Optional<PlacementRuleGenerator> getPlacement() {
        return placement;
    }

    public void addResource(ResourceSpecification resourceSpecification) {
        resources = new ArrayList<>(resources);
        resources.add(resourceSpecification);
    }
}
