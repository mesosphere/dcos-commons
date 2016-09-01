package org.apache.mesos.protobuf;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.VolumeSpecification;

/**
 * This class provides a default implementation of the VolumeSpecification interface.
 */
public class DefaultVolumeSpecification implements VolumeSpecification {
    private final Type type;
    private final String containerPath;
    private final String role;
    private final String principal;
    private final Protos.Value value;

    public DefaultVolumeSpecification(double diskSize, Type type, String containerPath, String role, String principal) {
        this.type = type;
        this.containerPath = containerPath;
        this.role = role;
        this.principal = principal;
        this.value = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(diskSize))
                .build();
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getContainerPath() {
        return containerPath;
    }

    @Override
    public Protos.Value getValue() {
        return value;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public String getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return "disk";
    }
}
