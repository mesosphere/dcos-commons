package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.ValueUtils;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by gabriel on 8/28/16.
 */
public class DefaultTaskSpecification implements TaskSpecification {
    private final String name;
    private final Protos.CommandInfo commandInfo;
    private final Collection<ResourceSpecification> resourceSpecifications;

    public static DefaultTaskSpecification create(Protos.TaskInfo taskInfo) {
        return new DefaultTaskSpecification(taskInfo.getName(), taskInfo.getCommand(), getResources(taskInfo));
    }

    public static DefaultTaskSpecification create(
            String name,
            Protos.CommandInfo commandInfo,
            Collection<ResourceSpecification> resourceSpecifications) {
        return new DefaultTaskSpecification(name, commandInfo, resourceSpecifications);
    }

    protected DefaultTaskSpecification(
            String name,
            Protos.CommandInfo commandInfo,
            Collection<ResourceSpecification> resourceSpecifications) {
        this.name = name;
        this.commandInfo = commandInfo;
        this.resourceSpecifications = resourceSpecifications;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Protos.CommandInfo getCommand() {
        return commandInfo;
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return resourceSpecifications;
    }

    private static Collection<ResourceSpecification> getResources(Protos.TaskInfo taskInfo) {
        Collection<ResourceSpecification> resourceSpecifications = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {
            resourceSpecifications.add(
                    new DefaultResourceSpecification(
                            resource.getName(),
                            ValueUtils.getValue(resource),
                            resource.getRole(),
                            resource.getReservation().getPrincipal()));
        }
        return resourceSpecifications;
    }

    @Override
    public String toString() {
        return "DefaultTaskSpecification{" +
                "name='" + name + '\'' +
                ", commandInfo=" + commandInfo +
                ", resourceSpecifications=" + resourceSpecifications +
                '}';
    }
}
