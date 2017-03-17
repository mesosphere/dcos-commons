package com.mesosphere.sdk.kafka.upgrade.old;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;


/* copy from dcos-kafka-service */

/**
 * copy from dcos-kafka-service.
 */
public class BrokerConfiguration {
    @JsonProperty("cpus")
    private double cpus;
    @JsonProperty("mem")
    private double mem;
    @JsonProperty("heap")
    private HeapConfig heap;
    @JsonProperty("disk")
    private double disk;
    @JsonProperty("disk_type")
    private String diskType;
    @JsonProperty("kafka_uri")
    private String kafkaUri;
    @JsonProperty("java_uri")
    private String javaUri;
    @JsonProperty("overrider_uri")
    private String overriderUri;
    @JsonProperty("port")
    private Long port;
    @JsonProperty("jmx")
    private JmxConfig jmx;
    @JsonProperty("statsd")
    private StatsdConfig statsd;

    public BrokerConfiguration() {

    }

    @JsonCreator
    public BrokerConfiguration(
            @JsonProperty("cpus") double cpus,
            @JsonProperty("mem") double mem,
            @JsonProperty("heap") HeapConfig heap,
            @JsonProperty("disk") double disk,
            @JsonProperty("disk_type") String diskType,
            @JsonProperty("kafka_uri") String kafkaUri,
            @JsonProperty("java_uri") String javaUri,
            @JsonProperty("overrider_uri") String overriderUri,
            @JsonProperty("port") Long port,
            @JsonProperty("jmx") JmxConfig jmx,
            @JsonProperty("statsd") StatsdConfig statsd) {
        this.cpus = cpus;
        this.mem = mem;
        this.heap = heap;
        this.disk = disk;
        this.diskType = diskType;
        this.kafkaUri = kafkaUri;
        this.javaUri = javaUri;
        this.overriderUri = overriderUri;
        this.port = port;
        this.jmx = jmx;
        this.statsd = statsd;
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

    public HeapConfig getHeap() {
        return heap;
    }

    @JsonProperty("heap")
    public void setHeap(HeapConfig heap) {
        this.heap = heap;
    }

    public double getDisk() {
        return disk;
    }

    @JsonProperty("disk")
    public void setDisk(double disk) {
        this.disk = disk;
    }

    public String getDiskType() {
        return diskType;
    }

    @JsonProperty("disk_type")
    public void setDiskType(String diskType) {
        this.diskType = diskType;
    }

    public String getKafkaUri() {
        return kafkaUri;
    }

    @JsonProperty("kafka_uri")
    public void setKafkaUri(String kafkaUri) {
        this.kafkaUri = kafkaUri;
    }

    public String getJavaUri() {
        return javaUri;
    }

    @JsonProperty("java_uri")
    public void setJavaUri(String javaUri) {
        this.javaUri = javaUri;
    }

    public String getOverriderUri() {
        return overriderUri;
    }

    @JsonProperty("overrider_uri")
    public void setOverriderUri(String overriderUri) {
        this.overriderUri = overriderUri;
    }

    public Long getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(Long port) {
        this.port = port;
    }

    public JmxConfig getJmx() {
        return jmx;
    }

    @JsonProperty("jmx")
    public void setJmx(JmxConfig jmx) {
        this.jmx = jmx;
    }

    public StatsdConfig getStatsd() {
        return statsd;
    }

    @JsonProperty("statsd")
    public void setStatsd(StatsdConfig statsd) {
        this.statsd = statsd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BrokerConfiguration that = (BrokerConfiguration) o;
        return Double.compare(that.cpus, cpus) == 0 &&
                Double.compare(that.mem, mem) == 0 &&
                Objects.equals(that.heap, heap) &&
                Double.compare(that.disk, disk) == 0 &&
                Objects.equals(diskType, that.diskType) &&
                Objects.equals(kafkaUri, that.kafkaUri) &&
                Objects.equals(javaUri, that.javaUri) &&
                Objects.equals(overriderUri, that.overriderUri) &&
                Objects.equals(port, that.port) &&
                Objects.equals(jmx, that.jmx) &&
                Objects.equals(statsd, that.statsd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpus, mem, heap, disk, diskType, kafkaUri, javaUri, overriderUri, port, jmx, statsd);
    }

    @Override
    public String toString() {
        return "BrokerConfiguration{" +
                "cpus=" + cpus +
                ", mem=" + mem +
                ", heap=" + heap +
                ", disk=" + disk +
                ", diskType='" + diskType + '\'' +
                ", kafkaUri='" + kafkaUri + '\'' +
                ", javaUri='" + javaUri + '\'' +
                ", overriderUri='" + overriderUri + '\'' +
                ", port='" + port + '\'' +
                ", jmx='" + jmx + '\'' +
                ", statsd='" + statsd + '\'' +
                '}';
    }
}
