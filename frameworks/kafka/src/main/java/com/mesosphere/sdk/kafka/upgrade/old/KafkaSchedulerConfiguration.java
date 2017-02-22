package com.mesosphere.sdk.kafka.upgrade.old;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.mesosphere.sdk.config.*;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/* copy from dcos-kafka-service */

/**
 * old config.
 */
@JsonSerialize
public class KafkaSchedulerConfiguration implements Configuration {

    private static final Log LOGGER = LogFactory.getLog(KafkaSchedulerConfiguration.class);
    private static final ConfigurationFactory<KafkaSchedulerConfiguration> FACTORY = new Factory();

    @JsonProperty("service")
    private ServiceConfiguration serviceConfiguration;

    @JsonProperty("broker")
    private BrokerConfiguration brokerConfiguration;

    @JsonProperty("kafka")
    private KafkaConfiguration kafkaConfiguration;

    @JsonProperty("executor")
    private ExecutorConfiguration executorConfiguration;

    @JsonProperty("recovery")
    private RecoveryConfiguration recoveryConfiguration;

    @JsonProperty("healthcheck")
    private KafkaHealthCheckConfiguration healthCheckConfiguration;

    public KafkaSchedulerConfiguration() {
    }

    @JsonCreator
    public KafkaSchedulerConfiguration(
            @JsonProperty("service") ServiceConfiguration serviceConfiguration,
            @JsonProperty("broker") BrokerConfiguration brokerConfiguration,
            @JsonProperty("kafka") KafkaConfiguration kafkaConfiguration,
            @JsonProperty("executor") ExecutorConfiguration executorConfiguration,
            @JsonProperty("recovery") RecoveryConfiguration recoveryConfiguration,
            @JsonProperty("healthcheck") KafkaHealthCheckConfiguration healthCheckConfiguration) {
        this.serviceConfiguration = serviceConfiguration;
        this.brokerConfiguration = brokerConfiguration;
        this.kafkaConfiguration = kafkaConfiguration;
        this.executorConfiguration = executorConfiguration;
        this.recoveryConfiguration = recoveryConfiguration;
        this.healthCheckConfiguration = healthCheckConfiguration;
    }

    @JsonProperty("service")
    public ServiceConfiguration getServiceConfiguration() {
        return serviceConfiguration;
    }

    @JsonProperty("service")
    public void setServiceConfiguration(ServiceConfiguration serviceConfiguration) {
        this.serviceConfiguration = serviceConfiguration;
    }

    @JsonProperty("broker")
    public BrokerConfiguration getBrokerConfiguration() {
        return brokerConfiguration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KafkaSchedulerConfiguration that = (KafkaSchedulerConfiguration) o;
        return Objects.equals(serviceConfiguration, that.serviceConfiguration) &&
                Objects.equals(brokerConfiguration, that.brokerConfiguration) &&
                Objects.equals(kafkaConfiguration, that.kafkaConfiguration) &&
                Objects.equals(executorConfiguration, that.executorConfiguration) &&
                Objects.equals(recoveryConfiguration, that.recoveryConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceConfiguration,
                brokerConfiguration, kafkaConfiguration, executorConfiguration, recoveryConfiguration);
    }

    @JsonProperty("broker")
    public void setBrokerConfiguration(BrokerConfiguration brokerConfiguration) {
        this.brokerConfiguration = brokerConfiguration;
    }

    @JsonProperty("kafka")
    public KafkaConfiguration getKafkaConfiguration() {
        return kafkaConfiguration;
    }

    @JsonProperty("kafka")
    public void setKafkaConfiguration(KafkaConfiguration kafkaConfiguration) {
        this.kafkaConfiguration = kafkaConfiguration;
    }

    @JsonProperty("executor")
    public ExecutorConfiguration getExecutorConfiguration() {
        return executorConfiguration;
    }

    @JsonProperty("executor")
    public void setExecutorConfiguration(ExecutorConfiguration executorConfiguration) {
        this.executorConfiguration = executorConfiguration;
    }

    @JsonProperty("recovery")
    public RecoveryConfiguration getRecoveryConfiguration() {
        return recoveryConfiguration;
    }

    @JsonProperty("recovery")
    public void setRecoveryConfiguration(RecoveryConfiguration recoveryConfiguration) {
        this.recoveryConfiguration = recoveryConfiguration;
    }

    @JsonProperty("healthcheck")
    public void setHealthCheckConfiguration(KafkaHealthCheckConfiguration healthCheckConfiguration) {
        this.healthCheckConfiguration = healthCheckConfiguration;
    }

    @JsonProperty("healthcheck")
    public KafkaHealthCheckConfiguration getHealthCheckConfiguration() {
        return healthCheckConfiguration;
    }

    @Override
    public String toString() {
        try {
            return toJsonString();
        } catch (Exception e) {
            return "Failed to stringify KafkaSchedulerConfiguration";
        }
    }

    @JsonIgnore
    @Override
    public byte[] getBytes() throws ConfigStoreException {
        try {
            return SerializationUtils.toJsonString(this).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Error occured while serializing the object: " + e);
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, e.getMessage(), e);
        }
    }

    public static ConfigurationFactory<KafkaSchedulerConfiguration> getFactoryInstance() {
        return FACTORY;
    }

    private static class Factory implements ConfigurationFactory<KafkaSchedulerConfiguration> {

        private final ObjectMapper objectMapper;

        private Factory() {
            // Be permissive of unknown fields, to avoid causing problems during downgrades:
            objectMapper = SerializationUtils.registerDefaultModules(new ObjectMapper())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        @Override
        public KafkaSchedulerConfiguration parse(byte[] bytes) throws ConfigStoreException {
            try {
                return SerializationUtils.fromString(
                        new String(bytes, StandardCharsets.UTF_8),
                        KafkaSchedulerConfiguration.class,
                        objectMapper);
            } catch (Exception e) {
                throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, e.getMessage(), e);
            }
        }
    }

    @Override
    public String toJsonString() throws ConfigStoreException {
        try {
            return SerializationUtils.toJsonString(this);
        } catch (IOException e) {
            throw new ConfigStoreException(Reason.SERIALIZATION_ERROR, e);
        }
    }
}
