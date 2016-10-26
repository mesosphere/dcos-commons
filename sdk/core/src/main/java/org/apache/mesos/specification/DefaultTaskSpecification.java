package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.offer.ValueUtils;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * This class provides a default implementation of the TaskSpecification interface.
 */
public class DefaultTaskSpecification implements TaskSpecification {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskSpecification.class);

    private final String name;
    private final String type;
    private final Optional<Protos.CommandInfo> commandInfo;
    private final Optional<Protos.ContainerInfo> containerInfo;
    private final Collection<ResourceSpecification> resourceSpecifications;
    private final Collection<VolumeSpecification> volumeSpecifications;
    private final Collection<ConfigFileSpecification> configFileSpecifications;
    private final Optional<PlacementRuleGenerator> placement;
    private final Optional<Protos.HealthCheck> healthCheck;

    public static DefaultTaskSpecification create(Protos.TaskInfo taskInfo)
            throws InvalidTaskSpecificationException, TaskException {
        return create(taskInfo, Optional.empty());
    }

    public static DefaultTaskSpecification create(Protos.TaskInfo taskInfo, Optional<PlacementRuleGenerator> placement)
            throws InvalidTaskSpecificationException, TaskException {
        //TODO(nick): Is the original placement constraint needed here?
        //  If this is just for relaunching a task in the same place then it's probably a moot point..
        //  Otherwise we'll need to implement serializing/deserializing the configured placement
        //  constraints in the TaskInfo itself. But from looking at the callers, it doesn't look like
        //  anything needs the placement constraints anyway.
        Collection<ConfigFileSpecification> configFiles;
        try {
            configFiles = TaskUtils.getConfigFiles(taskInfo);
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidTaskSpecificationException("Failed to deserialize config files: " + e.getMessage());
        }

        if (!taskInfo.hasCommand() && !taskInfo.hasContainer()) {
            throw new InvalidTaskSpecificationException("Both ContainerInfo and CommandInfo are missing, specify one.");
        }

        return new DefaultTaskSpecification(
                taskInfo.getName(),
                TaskUtils.getTaskType(taskInfo),
                taskInfo.hasContainer() ? Optional.of(taskInfo.getContainer()) : Optional.empty(),
                taskInfo.hasCommand() ? Optional.of(taskInfo.getCommand()) : Optional.empty(),
                getResources(taskInfo),
                getVolumes(taskInfo),
                configFiles,
                placement,
                taskInfo.hasHealthCheck() ? Optional.of(taskInfo.getHealthCheck()) : Optional.empty());
    }

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
            Optional<PlacementRuleGenerator> placement,
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
            Optional<PlacementRuleGenerator> placement,
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
            Optional<PlacementRuleGenerator> placement,
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
            @JsonProperty("placement") Optional<PlacementRuleGenerator> placement,
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
    public Optional<PlacementRuleGenerator> getPlacement() {
        return placement;
    }

    private static Collection<ResourceSpecification> getResources(Protos.TaskInfo taskInfo) {
        Collection<ResourceSpecification> resourceSpecifications = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {
            if (!resource.hasDisk()) {
                resourceSpecifications.add(
                        new DefaultResourceSpecification(
                                resource.getName(),
                                ValueUtils.getValue(resource),
                                resource.getRole(),
                                resource.getReservation().getPrincipal()));
            }
        }
        return resourceSpecifications;
    }

    private static Collection<VolumeSpecification> getVolumes(Protos.TaskInfo taskInfo)
            throws InvalidTaskSpecificationException {
        Collection<VolumeSpecification> volumeSpecifications = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {

            if (resource.hasDisk()) {
                VolumeSpecification.Type type = null;
                String containerPath = null;
                double size = resource.getScalar().getValue();
                Protos.Resource.DiskInfo diskInfo = resource.getDisk();
                type = getType(diskInfo);

                if (diskInfo.hasVolume()) {
                    Protos.Volume volume = diskInfo.getVolume();
                    containerPath = volume.getContainerPath();
                }

                if (type == null || containerPath == null) {
                    throw new InvalidTaskSpecificationException(
                            String.format(
                                    "Failed to reconstruct a volume from a TaskInfo. type: '%s', " +
                                            "containerPath: '%s', size: '%s'",
                                    type, containerPath, size));
                }

                volumeSpecifications.add(
                        new DefaultVolumeSpecification(
                                size,
                                type,
                                containerPath,
                                resource.getRole(),
                                resource.getReservation().getPrincipal()));
            }
        }

        return volumeSpecifications;
    }

    private static VolumeSpecification.Type getType(Protos.Resource.DiskInfo diskInfo) {
        if (diskInfo.hasSource()) {
            Protos.Resource.DiskInfo.Source.Type type = diskInfo.getSource().getType();
            switch (type) {
                case MOUNT:
                    return VolumeSpecification.Type.MOUNT;
                case PATH:
                    return VolumeSpecification.Type.PATH;
                default:
                    LOGGER.error("Encountered unexpected type: " + type + " defaulting to ROOT volume.");
                    return VolumeSpecification.Type.ROOT;
            }
        } else {
            return VolumeSpecification.Type.ROOT;
        }
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TaskSpecification)) {
            return false;
        }
        return !TaskUtils.areDifferent(this, (TaskSpecification) o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
