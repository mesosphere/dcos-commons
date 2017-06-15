package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.dcos.Capabilities;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Value;

import java.util.Optional;

/**
 * Wrapper around a Mesos {@link Resource}, combined with a resource ID string which should be present in the
 * {@link Resource} as a {@link Label}.
 **/
public class MesosResource {
    public static final String RESOURCE_ID_KEY = "resource_id";
    public static final String DYNAMIC_PORT_KEY = "dynamic_port";
    public static final String VIP_LABEL_NAME_KEY = "vip_key";
    public static final String VIP_LABEL_VALUE_KEY = "vip_value";

    private final Resource resource;

    public MesosResource(Resource resource) {
        this.resource = resource;
    }

    public Resource getResource() {
        return resource;
    }

    public boolean isAtomic() {
        return resource.hasDisk()
            && resource.getDisk().hasSource()
            && resource.getDisk().getSource().getType().equals(Source.Type.MOUNT);
    }

    public String getName() {
        return resource.getName();
    }

    public Value.Type getType() {
        return resource.getType();
    }

    public Optional<String> getResourceId() {
        return ResourceUtils.getResourceId(resource);
    }

    public boolean hasResourceId() {
        return ResourceUtils.hasResourceId(resource);
    }

    public Value getValue() {
        return ValueUtils.getValue(resource);
    }

    public String getRole() {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            return getRefinedRole();
        } else {
            return getLegacyRole();
        }
    }

    private String getRefinedRole() {
        if (resource.getReservationsCount() > 0) {
            return resource.getReservations(resource.getReservationsCount() - 1).getRole();
        }

        return Constants.ANY_ROLE;
    }

    private String getLegacyRole() {
        return resource.getRole();
    }

    public String getPreviousRole() {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            return getRefinedPreviousRole();
        } else {
            return getLegacyPreviousRole();
        }
    }

    private String getRefinedPreviousRole() {
        if (resource.getReservationsCount() <= 1) {
            return resource.getRole();
        } else {
            return resource.getReservations(resource.getReservationsCount() - 2).getRole();
        }
    }

    private String getLegacyPreviousRole() {
        return Constants.ANY_ROLE;
    }

    public Optional<String> getPrincipal() {
        return resource.hasReservation() && resource.getReservation().hasPrincipal()
                ? Optional.of(resource.getReservation().getPrincipal())
                : Optional.empty();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
