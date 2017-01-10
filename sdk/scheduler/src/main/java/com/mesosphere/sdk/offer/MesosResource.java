package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;
import org.apache.mesos.Protos.Value;

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
    private final String resourceId;

    public MesosResource(Resource resource) {
        this.resource = resource;
        this.resourceId = getResourceIdInternal(resource);
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

    public boolean hasResourceId() {
        return resourceId != null;
    }

    public String getResourceId() {
        return resourceId;
    }

    public boolean hasReservation() {
        return resource.hasReservation();
    }

    public Value getValue() {
        return ValueUtils.getValue(resource);
    }

    public String getRole() {
        return resource.getRole();
    }

    public String getPrincipal() {
        if (resource.hasReservation() && resource.getReservation().hasPrincipal()) {
            return resource.getReservation().getPrincipal();
        }

        return null;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    private static String getResourceIdInternal(Resource resource) {
        if (resource.hasReservation()) {
            for (Label label : resource.getReservation().getLabels().getLabelsList()) {
                if (label.getKey().equals(RESOURCE_ID_KEY)) {
                    return label.getValue();
                }
            }
        }
        return null;
    }
}
