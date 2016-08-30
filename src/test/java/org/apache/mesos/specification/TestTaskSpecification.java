package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by gabriel on 8/30/16.
 */
public class TestTaskSpecification implements TaskSpecification {
    private final String name;
    private final Protos.CommandInfo command;
    private Collection<ResourceSpecification> resources;

    public TestTaskSpecification(TaskSpecification taskSpecification) {
        this.name = taskSpecification.getName();
        this.command = taskSpecification.getCommand();
        this.resources = taskSpecification.getResources();
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

    public void addResource(ResourceSpecification resourceSpecification) {
        resources = new ArrayList<>(resources);
        resources.add(resourceSpecification);
    }
}
