package com.mesosphere.sdk.kafka.upgrade.old;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;


/* copy from dcos-kafka-service */

/**
 * copy from dcos-kafka-service.
 */
public class ExecutorConfiguration {
    @JsonProperty("cpus")
    private double cpus;
    @JsonProperty("mem")
    private double mem;
    @JsonProperty("disk")
    private double disk;
    @JsonProperty("executor_uri")
    private String executorUri;

    public ExecutorConfiguration() {

    }

    @JsonCreator
    public ExecutorConfiguration(
            @JsonProperty("cpus") double cpus,
            @JsonProperty("mem") double mem,
            @JsonProperty("disk") double disk,
            @JsonProperty("executor_uri") String executorUri) {
        this.cpus = cpus;
        this.mem = mem;
        this.disk = disk;
        this.executorUri = executorUri;
    }

    public double getCpus() {
        return cpus;
    }

    @JsonProperty("cpus")
    public void setCpus(double cpus) {
        this.cpus = cpus;
    }

    public double getMem() {
        return mem;
    }

    @JsonProperty("mem")
    public void setMem(double mem) {
        this.mem = mem;
    }

    @JsonProperty("disk")
    public double getDisk() {
        return disk;
    }

    @JsonProperty("disk")
    public void setDisk(double disk) {
        this.disk = disk;
    }

    public String getExecutorUri() {
        return executorUri;
    }

    @JsonProperty("executor_uri")
    public void setExecutorUri(String executorUri) {
        this.executorUri = executorUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExecutorConfiguration that = (ExecutorConfiguration) o;
        return Double.compare(that.cpus, cpus) == 0 &&
                Double.compare(that.mem, mem) == 0 &&
                Double.compare(that.disk, disk) == 0 &&
                Objects.equals(executorUri, that.executorUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpus, mem, disk, executorUri);
    }

    @Override
    public String toString() {
        return "ExecutorConfiguration{" +
                "cpus=" + cpus +
                ", mem=" + mem +
                ", disk=" + disk +
                ", executorUri='" + executorUri + '\'' +
                '}';
    }
}
