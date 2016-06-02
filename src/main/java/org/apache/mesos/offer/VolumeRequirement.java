package org.apache.mesos.offer;

/**
 * A VolumeRequirement encapsulates the configuration required for volume creation.
 */
public class VolumeRequirement {
    public static VolumeRequirement create() {
        return new VolumeRequirement();
    }

    private VolumeMode volumeMode = VolumeMode.NONE;
    private VolumeType volumeType = VolumeType.ROOT;

    private VolumeRequirement() {}

    public VolumeMode getVolumeMode() {
        return volumeMode;
    }

    public void setVolumeMode(VolumeMode volumeMode) {
        this.volumeMode = volumeMode;
    }

    public VolumeType getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(VolumeType volumeType) {
        this.volumeType = volumeType;
    }

    /**
     * VolumeMode.
     */
    public enum VolumeMode {
      NONE,
      EXISTING,
      CREATE
    }

    /**
     * VolumeType.
     */
    public enum VolumeType {
      ROOT,
      PATH,
      MOUNT
    }
}
