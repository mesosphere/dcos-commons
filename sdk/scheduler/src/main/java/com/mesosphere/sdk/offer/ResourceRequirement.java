package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Value;

/**
 * A ResourceRequirement encapsulate a needed Resource.
 * A Resource object indicates whether it is expected to exist already or
 * to be created by the presence or absence of IDs respectively.  A Resource
 * indicates that is expected to exist by having a Label with the key
 * "resource_id" attached to it.  A Volume simliarly indicates the same need
 * for creation or expected existence by having a persistence ID of an empty string
 * or an already determined value.
 */
public class ResourceRequirement {
    private MesosResource mesosResource;
    private DiskInfo diskInfo;

    public ResourceRequirement(Resource resource) {
        this.mesosResource = new MesosResource(resource);
        this.diskInfo = getDiskInfo();
    }

    public Resource getResource() {
        return mesosResource.getResource();
    }

    public String getRole() {
        return mesosResource.getRole();
    }

    public String getPrincipal() {
        return mesosResource.getPrincipal();
    }

    public String getName() {
        return mesosResource.getName();
    }

    public String getResourceId() {
        return mesosResource.getResourceId();
    }

    public String getPersistenceId() {
        if (hasPersistenceId()) {
            return diskInfo.getPersistence().getId();
        } else {
            return null;
        }
    }

    public boolean consumesUnreservedResource() {
        return !expectsResource() && !reservesResource();
    }

    public boolean reservesResource() {
        if (!hasReservation()) {
            return false;
        }

        return !expectsResource();
    }

    public boolean expectsResource() {
        if (hasResourceId() && !getResourceId().isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    public boolean expectsVolume() {
        if (!hasPersistenceId()) {
            return false;
        }

        String persistenceId = getPersistenceId();

        if (persistenceId.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public boolean createsVolume() {
        if (!hasPersistenceId()) {
            return false;
        }

        String persistenceId = getPersistenceId();

        if (persistenceId.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    public boolean needsVolume() {
        return expectsVolume() || createsVolume();
    }

    public boolean isAtomic() {
        return mesosResource.isAtomic();
    }

    public Value getValue() {
        return mesosResource.getValue();
    }

    private boolean hasResourceId() {
        return mesosResource.hasResourceId();
    }

    private boolean hasPersistenceId() {
        if (!hasDiskInfo()) {
            return false;
        }

        return diskInfo.hasPersistence();
    }

    private boolean hasReservation() {
        return mesosResource.hasReservation();
    }

    private boolean hasDiskInfo() {
        return mesosResource.getResource().hasDisk();
    }

    private DiskInfo getDiskInfo() {
        if (hasDiskInfo()) {
            return mesosResource.getResource().getDisk();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
