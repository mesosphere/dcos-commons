package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRule;

import java.util.Collection;
import java.util.Optional;

/**
 * This class provides a default implementation of the TaskSpecification interface.
 */
public class DefaultTaskSpecification implements TaskSpecification {

    private final String name;
    private final String type;
    private final Optional<Protos.CommandInfo> commandInfo;
    private final Optional<Protos.ContainerInfo> containerInfo;
    private final Collection<ResourceSpecification> resourceSpecifications;
    private final Collection<VolumeSpecification> volumeSpecifications;
    private final Collection<ConfigFileSpecification> configFileSpecifications;
    private final Optional<PlacementRule> placement;
    private final Optional<Protos.HealthCheck> healthCheck;

    /**
     * Create task with only {@link Protos.CommandInfo}.
     */
    protected DefaultTaskSpecification(
            String name,
            String type,
            Protos.CommandInfo commandInfo,
            Collection<ResourceSpecification> resourceSpecifications,
            Collection<VolumeSpecification> volumeSpecifications,
            Collection<ConfigFileSpecification> configFileSpecifications,
            Optional<PlacementRule> placement,
            Optional<Protos.HealthCheck> healthCheck) {

        this(name,
            type,
            Optional.empty(),
            Optional.of(commandInfo),
            resourceSpecifications,
            volumeSpecifications,
            configFileSpecifications,
            placement,
            healthCheck);
    }

    /**
     * Create task with only {@link Protos.ContainerInfo}.
     */
    protected DefaultTaskSpecification(
            String name,
            String type,
            Protos.ContainerInfo containerInfo,
            Collection<ResourceSpecification> resourceSpecifications,
            Collection<VolumeSpecification> volumeSpecifications,
            Collection<ConfigFileSpecification> configFileSpecifications,
            Optional<PlacementRule> placement,
            Optional<Protos.HealthCheck> healthCheck) {

        this(name,
            type,
            Optional.of(containerInfo),
            Optional.empty(),
            resourceSpecifications,
            volumeSpecifications,
            configFileSpecifications,
            placement,
            healthCheck);
    }

    /**
     * Create task with both {@link Protos.ContainerInfo} and {@link Protos.CommandInfo}.
     */
    protected DefaultTaskSpecification(
            String name,
            String type,
            Protos.ContainerInfo containerInfo,
            Protos.CommandInfo commandInfo,
            Collection<ResourceSpecification> resourceSpecifications,
            Collection<VolumeSpecification> volumeSpecifications,
            Collection<ConfigFileSpecification> configFileSpecifications,
            Optional<PlacementRule> placement,
            Optional<Protos.HealthCheck> healthCheck) {

        this(name,
            type,
            Optional.of(containerInfo),
            Optional.of(commandInfo),
            resourceSpecifications,
            volumeSpecifications,
            configFileSpecifications,
            placement,
            healthCheck);
    }

    @JsonCreator
    protected DefaultTaskSpecification(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("container") Optional<Protos.ContainerInfo> containerInfo,
            @JsonProperty("command") Optional<Protos.CommandInfo> commandInfo,
            @JsonProperty("resources") Collection<ResourceSpecification> resourceSpecifications,
            @JsonProperty("volumes") Collection<VolumeSpecification> volumeSpecifications,
            @JsonProperty("config_files") Collection<ConfigFileSpecification> configFileSpecifications,
            @JsonProperty("placement") Optional<PlacementRule> placement,
            @JsonProperty("health_check") Optional<Protos.HealthCheck> healthCheck) {
        this.name = name;
        this.type = type;
        this.containerInfo = containerInfo;
        this.commandInfo = commandInfo;
        this.resourceSpecifications = resourceSpecifications;
        this.volumeSpecifications = volumeSpecifications;
        this.configFileSpecifications = configFileSpecifications;
        this.placement = placement;
        this.healthCheck = healthCheck;
    }

    @Override
    public Optional<Protos.HealthCheck> getHealthCheck() {
        return healthCheck;
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
    public Optional<Protos.ContainerInfo> getContainer() {
        return containerInfo;
    }

    @Override
    public Optional<Protos.CommandInfo> getCommand() {
        return commandInfo;
    }

    @Override
    public Collection<ResourceSpecification> getResources() {
        return resourceSpecifications;
    }

    @Override
    public Collection<VolumeSpecification> getVolumes() {
        return volumeSpecifications;
    }

    @Override
    public Collection<ConfigFileSpecification> getConfigFiles() {
        return configFileSpecifications;
    }

    @Override
    public Optional<PlacementRule> getPlacement() {
        return placement;
    }

}
