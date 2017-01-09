package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.ResourceEvaluationStage;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Value;

/**
 * A {@link ResourceRequirement} encapsulates a needed {@link MesosResource}.
 *
 * A {@link MesosResource} object indicates whether it is expected to exist already or
 * to be created by the presence or absence of IDs respectively.  A {@link MesosResource}
 * indicates that it is expected to exist by having a Label with the key
 * {@code resource_id} attached to it.  A Volume simliarly indicates the same need
 * for creation or expected existence by having a persistence ID of an empty string
 * or an already determined value.
 */
public class ResourceRequirement {

    private final MesosResource mesosResource;

    public ResourceRequirement(Resource resource) {
        this.mesosResource = new MesosResource(resource);
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

    /**
     * Returns the volume persistence ID, or {@code null} if none is available.
     */
    public String getPersistenceId() {
        return hasPersistenceId() ? getDiskInfo().getPersistence().getId() : null;
    }

    /**
     * Returns whether this requirement is able to be fulfilled without requiring a resource reservation.
     */
    public boolean consumesUnreservedResource() {
        return !expectsResource() && !reservesResource();
    }

    /**
     * Returns whether this instance expects that a resource does not already exist on the destination and needs to be
     * reserved.
     */
    public boolean reservesResource() {
        return hasReservation() && !expectsResource();
    }

    /**
     * Returns whether this instance expects that a resource (with some associated resource ID) already exists on the
     * destination.
     */
    public boolean expectsResource() {
        return hasResourceId() && !getResourceId().isEmpty();
    }

    /**
     * Returns whether this instance expects that a volume (with some associated persistence ID) already exists on the
     * destination.
     */
    public boolean expectsVolume() {
        return hasPersistenceId() && !getPersistenceId().isEmpty();
    }

    /**
     * Returns whether this instance expects that a volume does not already exist on the destination and needs to be
     * created.
     */
    public boolean createsVolume() {
        return hasPersistenceId() && getPersistenceId().isEmpty();
    }

    /**
     * Returns whether this resource is atomic, i.e. cannot be split into smaller reservations. This typically applies
     * to mount volumes which cannot be shared across containers. It does not apply to e.g. CPU or Memory which can be
     * subdivided and shared.
     */
    public boolean isAtomic() {
        return mesosResource.isAtomic();
    }

    /**
     * Returns the amount of the resource to be reserved, e.g. the amount of CPU to reserve.
     */
    public Value getValue() {
        return mesosResource.getValue();
    }

    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new ResourceEvaluationStage(getResource(), taskName);
    }

    private boolean hasResourceId() {
        return mesosResource.hasResourceId();
    }

    private boolean hasPersistenceId() {
        DiskInfo diskInfo = getDiskInfo();
        return diskInfo != null && diskInfo.hasPersistence();
    }

    private boolean hasReservation() {
        return mesosResource.hasReservation();
    }

    private DiskInfo getDiskInfo() {
        return mesosResource.getResource().hasDisk() ? mesosResource.getResource().getDisk() : null;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
