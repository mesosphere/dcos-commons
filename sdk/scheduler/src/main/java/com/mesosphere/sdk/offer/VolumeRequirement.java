package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.VolumeEvaluationStage;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A VolumeRequirement encapsulates the configuration required for volume creation.
 */
public class VolumeRequirement extends ResourceRequirement {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String diskType;
    private final String persistenceId;
    private final boolean createsVolume;
    private String containerPath;

    protected VolumeRequirement(Builder builder) {
        super(builder);
        this.diskType = builder.diskType;
        this.persistenceId = builder.persistenceId;
        this.createsVolume = builder.createsVolume;
        this.containerPath = builder.containerPath;
    }

    public static Builder newBuilder(String role, Protos.Value value, String containerPath) {
        return new Builder(role, value, containerPath);
    }

    public static Builder newBuilder(Protos.Resource resource) {
        return new Builder(resource);
    }

    public String getDiskType() {
        return diskType;
    }

    public String getPersistenceId() {
        return persistenceId;
    }

    public boolean createsVolume() {
        return createsVolume;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return null;
    }

    @Override
    public Optional<MesosResource> satisfy(MesosResourcePool pool) {
        // Evaluate disk resource requirement first, then evaluate volume requirement

        List<OfferRecommendation> offerRecommendations = new ArrayList<>();

        // If it's not a MOUNT volume or it's already been reserved use default offer evaluation.
        if (!getDiskType().equals(Constants.MOUNT_DISK_TYPE) || expectsResource()) {
            return super.satisfy(pool);
        }

        return pool.consumeAtomic(getName(), getValue());
    }

    public static class Builder extends ResourceRequirement.Builder {
        private String diskType;
        private String persistenceId;
        private String containerPath;
        private boolean createsVolume;

        protected Builder(String role, Protos.Value value, String containerPath) {
            super(role, Constants.DISK_RESOURCE_TYPE, value);
            containerPath(containerPath);
            diskType(Constants.ROOT_DISK_TYPE);
        }

        protected Builder(Protos.Resource resource) {
            this(resource.getRole(), ValueUtils.getValue(resource), getContainerPath(resource));
            diskType(getDiskType(resource));
            persistenceId(getPeristenceId(resource));

            if (persistenceId == null) {
                createsVolume(true);
            } else {
                createsVolume(false);
            }
        }

        public Builder diskType(String diskType) {
            this.diskType = diskType;
            return this;
        }

        public Builder containerPath(String containerPath) {
            this.containerPath = containerPath;
            return this;
        }

        public Builder persistenceId(String persistenceId) {
            this.persistenceId = persistenceId;
            return this;
        }

        public Builder createsVolume(boolean createsVolume) {
            this.createsVolume = createsVolume;
            return this;
        }

        public VolumeRequirement build() {
            validate();
            return new VolumeRequirement(this);
        }

        private void validate() throws IllegalStateException {
            if (diskType != Constants.ROOT_DISK_TYPE && diskType != Constants.MOUNT_DISK_TYPE) {
                throw new IllegalStateException(String.format("%s must be ROOT or MOUNT", diskType));
            }

            if (containerPath == null) {
                throw new IllegalStateException("Container path must be set.");
            }
        }

        private String getDiskType(Protos.Resource resource) {
            if (!resource.hasDisk()) {
                throw new IllegalStateException("Resource must have a disk.");
            }

            if (!resource.getDisk().hasSource()) {
                return Constants.ROOT_DISK_TYPE;
            }

            Protos.Resource.DiskInfo.Source.Type diskType = resource.getDisk().getSource().getType();

            switch (diskType) {
                case MOUNT:
                    return Constants.MOUNT_DISK_TYPE;
                default:
                    throw new IllegalStateException(String.format("Unexpected disk type: '%s'", diskType));
            }
        }

        private String getPeristenceId(Protos.Resource resource) {
            if (!resource.hasDisk()) {
                throw new IllegalStateException("Resource must have a disk.");
            }

            if (resource.getDisk().hasPersistence()) {
                return resource.getDisk().getPersistence().getId();
            }

            return null;
        }

        private static String getContainerPath(Protos.Resource resource) {
            if (!resource.hasDisk()) {
                throw new IllegalStateException("Resource must have a disk.");
            }

            if (resource.getDisk().hasVolume() && resource.getDisk().getVolume().hasContainerPath()) {
                return resource.getDisk().getVolume().getContainerPath();
            }

            return null;
        }
    }
}
