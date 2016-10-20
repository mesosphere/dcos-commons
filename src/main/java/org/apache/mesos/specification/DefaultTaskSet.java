package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
        return create(count, name, command, resources, volumes, Optional.empty(), Optional.empty());
    }

    public static DefaultTaskSet create(
            int count,
            String name,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes,
            Optional<PlacementRuleGenerator> placementOptional,
            Optional<Protos.HealthCheck> healthCheck) {

        List<TaskSpecification> taskSpecifications = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            taskSpecifications.add(new DefaultTaskSpecification(
                    name + "-" + i,
                    command,
                    resources,
                    volumes,
                    placementOptional,
                    healthCheck));
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
