package com.mesosphere.sdk.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

        if (gracePeriodSecs != that.gracePeriodSecs) {
            return false;
        }
        if (recoverDelaySecs != that.recoverDelaySecs) {
            return false;
        }
        return enableReplacement == that.enableReplacement;

    }

    @Override
    public int hashCode() {
        int result = gracePeriodSecs;
        result = 31 * result + recoverDelaySecs;
        result = 31 * result + (enableReplacement ? 1 : 0);
        return result;
    }

    @JsonProperty("recover-in-place-grace-period-secs")
    private int gracePeriodSecs;
    @JsonProperty("min-delay-between-recoveries-secs")
    private int recoverDelaySecs;
    @JsonProperty("enable-replacement")
    private boolean enableReplacement;

    public RecoveryConfiguration() {}

    @JsonCreator
    public RecoveryConfiguration(
            @JsonProperty("recover-in-place-grace-period-secs") int gracePeriodSecs,
            @JsonProperty("min-delay-between-recoveries-secs") int recoverDelaySecs,
            @JsonProperty("enable-replacement") boolean enableReplacement) {
        this.gracePeriodSecs = gracePeriodSecs;
        this.recoverDelaySecs = recoverDelaySecs;
        this.enableReplacement = enableReplacement;
    }

    @JsonIgnore
    public boolean isReplacementEnabled() {
        return enableReplacement;
    }

    @JsonProperty("enable-replacement")
    public void setEnableReplacement(boolean enableReplacement) {
        this.enableReplacement = enableReplacement;
    }

    @JsonProperty("min-delay-between-recoveries-secs")
    public int getRecoveryDelaySecs() {
        return recoverDelaySecs;
    }

    @JsonProperty("min-delay-between-recoveries-secs")
    public void setRecoveryDelaySecs(int recoverDelaySecs) {
        this.recoverDelaySecs = recoverDelaySecs;
    }

    public int getGracePeriodSecs() {
        return gracePeriodSecs;
    }

    @JsonProperty("recover-in-place-grace-period-secs")
    public void setGracePeriodSecs(int gracePeriodSecs) {
        this.gracePeriodSecs = gracePeriodSecs;
    }

    @Override
    public String toString() {
        return "RecoveryConfiguration{" +
                "gracePeriodSecs=" + gracePeriodSecs +
                ", recoverDelaySecs=" + recoverDelaySecs +
                ", enableReplacement=" + enableReplacement +
                '}';
    }
}
