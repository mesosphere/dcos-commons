package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.ValueUtils;
import org.apache.mesos.protobuf.DefaultVolumeSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

/**
 * This class provides a default implementation of the TaskSpecification interface.
 */
public class DefaultTaskSpecification implements TaskSpecification {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskSpecification.class);
    private final String name;
    private final Protos.CommandInfo commandInfo;
    private final Collection<ResourceSpecification> resourceSpecifications;
    private final Collection<VolumeSpecification> volumeSpecifications;

    public static DefaultTaskSpecification create(Protos.TaskInfo taskInfo) throws InvalidTaskSpecificationException {
        return new DefaultTaskSpecification(
                taskInfo.getName(),
                taskInfo.getCommand(),
                getResources(taskInfo),
                getVolumes(taskInfo));
    }

    protected DefaultTaskSpecification(
            String name,
            Protos.CommandInfo commandInfo,
            Collection<ResourceSpecification> resourceSpecifications,
            Collection<VolumeSpecification> volumeSpecifications) {
        this.name = name;
        this.commandInfo = commandInfo;
        this.resourceSpecifications = resourceSpecifications;
        this.volumeSpecifications = volumeSpecifications;
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

    @Override
    public Collection<VolumeSpecification> getVolumes() {
        return volumeSpecifications;
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
}
