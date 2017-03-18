package com.mesosphere.sdk.kafka.upgrade.old;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;


/* copy from dcos-kafka-service */

/**
 * This class encapsulates the configuration of the Broker HealthCheck.
 */
public class KafkaHealthCheckConfiguration {
    @JsonProperty("broker_health_check_enabled")
    private boolean enableHealthCheck;

    @JsonProperty("broker_health_check_delay_sec")
    private int healthCheckDelaySec;

    @JsonProperty("broker_health_check_interval_sec")
    private int healthCheckIntervalSec;

    @JsonProperty("broker_health_check_timeout_sec")
    private int healthCheckTimeoutSec;

    @JsonProperty("broker_health_check_max_consecutive_failures")
    private int healthCheckMaxFailures;

    @JsonProperty("broker_health_check_grace_period_sec")
    private int healthCheckGracePeriodSec;

    public KafkaHealthCheckConfiguration() {
    }

    @JsonCreator
    public KafkaHealthCheckConfiguration(
            @JsonProperty("broker_health_check_enabled") boolean enableHealthCheck,
            @JsonProperty("broker_health_check_delay_sec") int healthCheckDelaySec,
            @JsonProperty("broker_health_check_interval_sec") int healthCheckIntervalSec,
            @JsonProperty("broker_health_check_timeout_sec") int healthCheckTimeoutSec,
            @JsonProperty("broker_health_check_max_consecutive_failures") int healthCheckMaxFailures,
            @JsonProperty("broker_health_check_grace_period_sec") int healthCheckGracePeriodSec) {
        this.enableHealthCheck = enableHealthCheck;
        this.healthCheckDelaySec = healthCheckDelaySec;
        this.healthCheckIntervalSec = healthCheckIntervalSec;
        this.healthCheckTimeoutSec = healthCheckTimeoutSec;
        this.healthCheckMaxFailures = healthCheckMaxFailures;
        this.healthCheckGracePeriodSec = healthCheckGracePeriodSec;
    }

    @JsonProperty("broker_health_check_enabled")
    public void setEnableHealthCheck(boolean enableHealthCheck) {
        this.enableHealthCheck = enableHealthCheck;
    }

    @JsonProperty("broker_health_check_delay_sec")
    public void setHealthCheckDelaySec(int healthCheckDelaySec) {
        this.healthCheckDelaySec = healthCheckDelaySec;
    }

    @JsonProperty("broker_health_check_interval_sec")
    public void setHealthCheckIntervalSec(int healthCheckIntervalSec) {
        this.healthCheckIntervalSec = healthCheckIntervalSec;
    }

    @JsonProperty("broker_health_check_timeout_sec")
    public void setHealthCheckTimeoutSec(int healthCheckTimeoutSec) {
        this.healthCheckTimeoutSec = healthCheckTimeoutSec;
    }

    @JsonProperty("broker_health_check_max_consecutive_failures")
    public void setHealthCheckMaxFailures(int healthCheckMaxFailures) {
        this.healthCheckMaxFailures = healthCheckMaxFailures;
    }

    @JsonProperty("broker_health_check_grace_period_sec")
    public void setHealthCheckGracePeriodSec(int healthCheckGracePeriodSec) {
        this.healthCheckGracePeriodSec = healthCheckGracePeriodSec;
    }

    @JsonIgnore
    public boolean isHealthCheckEnabled() {
        return enableHealthCheck;
    }

    @JsonIgnore
    public Duration getHealthCheckDelay() {
        return Duration.ofSeconds(healthCheckDelaySec);
    }

    @JsonIgnore
    public Duration getHealthCheckInterval() {
        return Duration.ofSeconds(healthCheckIntervalSec);
    }

    @JsonIgnore
    public Duration getHealthCheckTimeout() {
        return Duration.ofSeconds(healthCheckTimeoutSec);
    }

    @JsonIgnore
    public int getHealthCheckMaxFailures() {
        return healthCheckMaxFailures;
    }

    @JsonIgnore
    public Duration getHealthCheckGracePeriod() {
        return Duration.ofSeconds(healthCheckGracePeriodSec);
    }

    @Override
    public String toString() {
        return "KafkaRecoveryConfiguration{" +
                "enableHealthCheck=" + enableHealthCheck +
                ", healthCheckDelaySec=" + healthCheckDelaySec +
                ", healthCheckIntervalSec=" + healthCheckIntervalSec +
                ", healthCheckTimeoutSec=" + healthCheckTimeoutSec +
                ", healthCheckMaxFailures=" + healthCheckMaxFailures +
                ", healthCheckGracePeriodSec=" + healthCheckGracePeriodSec +
                '}';
    }

    @Override
    @SuppressWarnings("PMD.IfStmtsMustUseBraces")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        KafkaHealthCheckConfiguration that = (KafkaHealthCheckConfiguration) o;

        if (enableHealthCheck != that.enableHealthCheck) return false;
        if (healthCheckDelaySec != that.healthCheckDelaySec) return false;
        if (healthCheckIntervalSec != that.healthCheckIntervalSec) return false;
        if (healthCheckTimeoutSec != that.healthCheckTimeoutSec) return false;
        if (healthCheckMaxFailures != that.healthCheckMaxFailures) return false;
        return healthCheckGracePeriodSec == that.healthCheckGracePeriodSec;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (enableHealthCheck ? 1 : 0);
        result = 31 * result + healthCheckDelaySec;
        result = 31 * result + healthCheckIntervalSec;
        result = 31 * result + healthCheckTimeoutSec;
        result = 31 * result + healthCheckMaxFailures;
        result = 31 * result + healthCheckGracePeriodSec;
        return result;
    }
}
