package org.apache.mesos.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the recovery configuration in JSON.
 */
public class RecoveryConfiguration {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
        return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RecoveryConfiguration that = (RecoveryConfiguration) o;

        if (gracePeriodMins != that.gracePeriodMins) {
            return false;
        }
        if (repairDelaySecs != that.repairDelaySecs) {
            return false;
        }
        return enableReplacement == that.enableReplacement;

    }

    @Override
    public int hashCode() {
        int result = gracePeriodMins;
        result = 31 * result + repairDelaySecs;
        result = 31 * result + (enableReplacement ? 1 : 0);
        return result;
    }

    @JsonProperty("repair_in_place_grace_period_mins")
    private int gracePeriodMins;
    @JsonProperty("min_delay_between_repairs_secs")
    private int repairDelaySecs;
    @JsonProperty("enable_replacement")
    private boolean enableReplacement;

    public RecoveryConfiguration() {}

    @JsonCreator
    public RecoveryConfiguration(
            @JsonProperty("repair_in_place_grace_period_mins") int gracePeriodMins,
            @JsonProperty("min_delay_between_repairs_secs") int repairDelaySecs,
            @JsonProperty("enable_replacement") boolean enableReplacement) {
        this.gracePeriodMins = gracePeriodMins;
        this.repairDelaySecs = repairDelaySecs;
        this.enableReplacement = enableReplacement;
    }

    public boolean isReplacementEnabled() {
        return enableReplacement;
    }

    @JsonProperty("enable_replacement")
    public void setEnableReplacement(boolean enableReplacement) {
        this.enableReplacement = enableReplacement;
    }

    public int getRepairDelaySecs() {
        return repairDelaySecs;
    }

    @JsonProperty("min_delay_between_repairs_secs")
    public void setRepairDelaySecs(int repairDelaySecs) {
        this.repairDelaySecs = repairDelaySecs;
    }

    public int getGracePeriodMins() {
        return gracePeriodMins;
    }

    @JsonProperty("repair_in_place_grace_period_mins")
    public void setGracePeriodMins(int gracePeriodMins) {
        this.gracePeriodMins = gracePeriodMins;
    }

    @Override
    public String toString() {
        return "RecoveryConfiguration{" +
                "gracePeriodMins=" + gracePeriodMins +
                ", repairDelaySecs=" + repairDelaySecs +
                ", enableReplacement=" + enableReplacement +
                '}';
    }
}
