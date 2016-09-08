package org.apache.mesos.protobuf;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
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

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    @SuppressWarnings("PMD.IfStmtsMustUseBraces")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultVolumeSpecification that = (DefaultVolumeSpecification) o;

        if (type != that.type) return false;
        if (containerPath != null ? !containerPath.equals(that.containerPath) : that.containerPath != null)
            return false;
        if (role != null ? !role.equals(that.role) : that.role != null) return false;
        if (principal != null ? !principal.equals(that.principal) : that.principal != null) return false;
        return value != null ? value.equals(that.value) : that.value == null;

    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (containerPath != null ? containerPath.hashCode() : 0);
        result = 31 * result + (role != null ? role.hashCode() : 0);
        result = 31 * result + (principal != null ? principal.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
