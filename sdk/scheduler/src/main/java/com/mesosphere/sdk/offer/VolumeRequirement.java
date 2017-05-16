package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;
import com.mesosphere.sdk.offer.evaluate.VolumeEvaluationStage;
import org.apache.mesos.Protos;

/**
 * A VolumeRequirement encapsulates the configuration required for volume creation.
 */
public class VolumeRequirement extends ResourceRequirement {
    private final String diskType;

    protected VolumeRequirement(Builder builder) {
        super(builder);
        this.diskType = builder.diskType;
    }

    public static Builder newBuilder(String role, Protos.Value value) {
        return new Builder(role, value);
    }

    public static Builder newBuilder(Protos.Resource resource) {
        return new Builder(resource);
    }

    public String getDiskType() {
        return diskType;
    }

    @Override
    public OfferEvaluationStage getEvaluationStage(String taskName) {
        return new VolumeEvaluationStage(this, taskName);
    }

    public static class Builder extends ResourceRequirement.Builder {
        private String diskType;

        protected Builder(String role, Protos.Value value) {
            super(role, Constants.DISK_RESOURCE_TYPE, value);
            diskType(Constants.ROOT_DISK_TYPE);
        }

        protected Builder(Protos.Resource resource) {
            this(resource.getRole(), ValueUtils.getValue(resource));
            diskType(getDiskType(resource));
        }

        public Builder diskType(String diskType) {
            this.diskType = diskType;
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
    }
}
