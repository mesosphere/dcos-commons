package org.apache.mesos.specification;

import org.apache.mesos.Protos;

import java.util.Collection;

/**
 * Created by gabriel on 8/29/16.
 */
public class DefaultTaskTypeSpecification implements TaskTypeSpecification {
    private final int count;
    private final String name;
    private final Protos.CommandInfo command;
    private final Collection<ResourceSpecification> resources;

    @Override
    public String toString() {
        return "DefaultTaskTypeSpecification{" +
                "count=" + count +
                ", name='" + name + '\'' +
                ", command=" + command +
                ", resources=" + resources +
                '}';
    }

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
}
