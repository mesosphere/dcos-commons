package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
            String type,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes) {

        return create(
                count,
                name,
                Optional.empty(),
                Optional.of(command),
                resources,
                volumes,
                new ArrayList<>() /* configs */,
                Optional.empty() /* placement */,
                Optional.empty() /* healthcheck */);
    }

    public static DefaultTaskSet create(
            int count,
            String name,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes,
            Collection<ConfigFileSpecification> configs,
            Optional<PlacementRuleGenerator> placementOptional,
            Optional<Protos.HealthCheck> healthCheck) {

        return create(
                count,
                name,
                Optional.empty(),
                Optional.of(command),
                resources,
                volumes,
                configs,
                placementOptional,
                healthCheck);
    }

    public static DefaultTaskSet create(
            int count,
            String name,
            Protos.ContainerInfo container,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes,
            Collection<ConfigFileSpecification> configs,
            Optional<PlacementRuleGenerator> placementOptional,
            Optional<Protos.HealthCheck> healthCheck) {

        return create(
                count,
                name,
                Optional.of(container),
                Optional.empty(),
                resources,
                volumes,
                configs,
                placementOptional,
                healthCheck);
    }

    public static DefaultTaskSet create(
            int count,
            String name,
            Protos.ContainerInfo container,
            Protos.CommandInfo command,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes,
            Collection<ConfigFileSpecification> configs,
            Optional<PlacementRuleGenerator> placementOptional,
            Optional<Protos.HealthCheck> healthCheck) {

        return create(
                count,
                name,
                Optional.of(container),
                Optional.of(command),
                resources,
                volumes,
                configs,
                placementOptional,
                healthCheck);
    }

    private static DefaultTaskSet create(
            int count,
            String name,
            Optional<Protos.ContainerInfo> container,
            Optional<Protos.CommandInfo> command,
            Collection<ResourceSpecification> resources,
            Collection<VolumeSpecification> volumes,
            Collection<ConfigFileSpecification> configs,
            Optional<PlacementRuleGenerator> placementOptional,
            Optional<Protos.HealthCheck> healthCheck) {

        List<TaskSpecification> taskSpecifications = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            taskSpecifications.add(new DefaultTaskSpecification(
                    name + "-" + i,
                    name,
                    container,
                    command,
                    resources,
                    volumes,
                    configs,
                    placementOptional,
                    healthCheck));
        }

        return create(name, taskSpecifications);
    }

    @JsonCreator
    public static DefaultTaskSet create(
            @JsonProperty("name") String name,
            @JsonProperty("tasks") List<TaskSpecification> taskSpecifications) {
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

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
