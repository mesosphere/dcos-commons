package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.Collection;

/**
 * This implementation of the TaskSpecification is for test purposes.  It allows what would otherwise be bad practices
 * like changing the ResourceSpecifications encapsulated by the TaskSpecification after construction.
 */
public class TestTaskSpecification implements TaskSpecification {
    private final String name;
    private final Protos.CommandInfo command;
    private final Collection<VolumeSpecification> volumes;
    private Collection<ResourceSpecification> resources;

    public TestTaskSpecification(TaskSpecification taskSpecification) {
        this.name = taskSpecification.getName();
        this.command = taskSpecification.getCommand();
        this.resources = taskSpecification.getResources();
        this.volumes = taskSpecification.getVolumes();
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
    public Collection<VolumeSpecification> getVolumes() {
        return volumes;
    }

    public void addResource(ResourceSpecification resourceSpecification) {
        resources = new ArrayList<>(resources);
        resources.add(resourceSpecification);
    }
}
