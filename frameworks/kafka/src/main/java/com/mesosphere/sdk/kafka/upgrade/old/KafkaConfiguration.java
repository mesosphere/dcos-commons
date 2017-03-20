package com.mesosphere.sdk.kafka.upgrade.old;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.CaseFormat;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;


/* copy from dcos-kafka-service */

/**
 * old config.
 */
public class KafkaConfiguration {
    @JsonProperty("kafka_advertise_host_ip")
    private boolean kafkaAdvertiseHostIp;

    @JsonProperty("kafka_ver_name")
    private String kafkaVerName;

    @JsonProperty("kafka_sandbox_path")
    private String kafkaSandboxPath;

    @JsonProperty("kafka_zk_uri")
    private String kafkaZkUri;

    @JsonProperty("mesos_zk_uri")
    private String mesosZkUri;

    // Note: We don't directly use this data, we just store it to detect when values have changed
    // via config updates. Do not remove.
    @JsonProperty("overrides")
    private Map<String, String> overrides;

    public KafkaConfiguration() {
        // No overrides provided. Auto-populate from process environment.
        overrides = KafkaEnvConfigUtils.getKafkaConfig(getSystemEnv());
    }

    @JsonCreator
    public KafkaConfiguration(
            @JsonProperty("kafka_advertise_host_ip") boolean kafkaAdvertiseHostIp,
            @JsonProperty("kafka_ver_name") String kafkaVerName,
            @JsonProperty("kafka_sandbox_path") String kafkaSandboxPath,
            @JsonProperty("kafka_zk_uri") String kafkaZkUri,
            @JsonProperty("mesos_zk_uri") String mesosZkUri,
            @JsonProperty("overrides") Map<String, String> overrides) {
        this.kafkaAdvertiseHostIp = kafkaAdvertiseHostIp;
        this.kafkaVerName = kafkaVerName;
        this.kafkaSandboxPath = kafkaSandboxPath;
        this.kafkaZkUri = kafkaZkUri;
        this.mesosZkUri = mesosZkUri;
        if (overrides == null || overrides.isEmpty()) {
            // No overrides provided by yaml. Auto-populate from process environment.
            this.overrides = KafkaEnvConfigUtils.getKafkaConfig(getSystemEnv());
        } else {
            // Use provided values.
            this.overrides = overrides;
        }
    }

    public boolean isKafkaAdvertiseHostIp() {
        return kafkaAdvertiseHostIp;
    }

    @JsonProperty("kafka_advertise_host_ip")
    public void setKafkaAdvertiseHostIp(boolean kafkaAdvertiseHostIp) {
        this.kafkaAdvertiseHostIp = kafkaAdvertiseHostIp;
    }

    public String getKafkaVerName() {
        return kafkaVerName;
    }

    @JsonProperty("kafka_ver_name")
    public void setKafkaVerName(String kafkaVerName) {
        this.kafkaVerName = kafkaVerName;
    }

    public String getKafkaSandboxPath() {
        return kafkaSandboxPath;
    }

    @JsonProperty("kafka_sandbox_path")
    public void setKafkaSandboxPath(String kafkaSandboxPath) {
        this.kafkaSandboxPath = kafkaSandboxPath;
    }

    public String getKafkaZkUri() {
        return kafkaZkUri;
    }

    @JsonProperty("kafka_zk_uri")
    public void setKafkaZkUri(String kafkaZkUri) {
        this.kafkaZkUri = kafkaZkUri;
    }

    @JsonProperty("mesos_zk_uri")
    public String getMesosZkUri() {
        return mesosZkUri;
    }

    @JsonProperty("mesos_zk_uri")
    public void setMesosZkUri(String mesosZkUri) {
        this.mesosZkUri = mesosZkUri;
    }

    /**
     * Returns a list of override settings to be passed directly to Kafka's server.properties file.
     * Returned keys are of the form "some.kafka.setting".
     */
    @JsonProperty("overrides")
    public Map<String, String> getOverrides() {
        // TODO(nick): This conversion is for backwards compatibility with persisted configs
        // produced by 1.1.9-0.10.0.0 and earlier.
        // ** In Jan 2017, remove all the following and just 'return overrides'. **
        if (overrides == null) {
            return overrides;
        }
        Map<String, String> normalizedOverrides = new TreeMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            normalizedOverrides.put(normalizedOverrideKey(entry.getKey()), entry.getValue());
        }
        return normalizedOverrides;
    }

    @JsonProperty("overrides")
    public void setOverrides(Map<String, String> overrides) {
        this.overrides = overrides;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        KafkaConfiguration that = (KafkaConfiguration) o;
        return kafkaAdvertiseHostIp == that.kafkaAdvertiseHostIp &&
                Objects.equals(kafkaVerName, that.kafkaVerName) &&
                Objects.equals(kafkaZkUri, that.kafkaZkUri) &&
                Objects.equals(mesosZkUri, that.mesosZkUri) &&
                Objects.equals(overrides, that.overrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kafkaAdvertiseHostIp, kafkaVerName, kafkaZkUri, mesosZkUri, overrides);
    }

    @Override
    public String toString() {
        return "KafkaConfiguration{" +
                "kafkaAdvertiseHostIp=" + kafkaAdvertiseHostIp +
                ", kafkaVerName='" + kafkaVerName + '\'' +
                ", kafkaSandboxPath='" + kafkaSandboxPath + '\'' +
                ", kafkaZkUri='" + kafkaZkUri + '\'' +
                ", mesosZkUri='" + mesosZkUri + '\'' +
                ", overrides=" + overrides +
                '}';
    }

    /**
     * Returns the system environment. Broken out into a protected function to allow testing.
     */
    protected Map<String, String> getSystemEnv() {
        return System.getenv();
    }

    /**
     * Convert the provided string from "someKafkaKey" (old fixed-yaml format) to "some.kafka.key",
     * or does nothing if the provided string is already in the latter format.
     */
    private static String normalizedOverrideKey(String origKey) {
        // someKafkaKey => some_kafka_key => some.kafka.key
        String fixedKey = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, origKey);
        return fixedKey.replace('_', '.');
    }
}
