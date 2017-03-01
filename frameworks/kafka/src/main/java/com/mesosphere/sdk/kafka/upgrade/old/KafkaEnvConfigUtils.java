package com.mesosphere.sdk.kafka.upgrade.old;

import java.util.Map;
import java.util.TreeMap;

/**
 * Utilities for passing configuration settings to Kafka's .properties file via environment
 * variables.
 *
 */
public class KafkaEnvConfigUtils {
    public static final String KAFKA_OVERRIDE_PREFIX = "KAFKA_OVERRIDE_";

    private KafkaEnvConfigUtils() {
        // Do not instantiate.
    }

    /**
     * Returns a suitable environment name for the provided dot-delimited key of the form
     * "some.kafka.key".
     *
     * @param kafkaConfigKey a dot-delimited config key of the form "some.kafka.key"
     * @see toKafkaKeyOrNull for the reverse operation
     */
    public static String toEnvName(String kafkaConfigKey) {
        // some.kafka.key => some_kafka_key => SOME_KAFKA_KEY => KAFKA_OVERRIDE_SOME_KAFKA_KEY
        return KAFKA_OVERRIDE_PREFIX + kafkaConfigKey.replace('.', '_').toUpperCase();
    }

    /**
     * Returns a map containing all Kafka configuration settings from the provided system
     * environment.
     *
     * @return map whose keys are of the form "some.kafka.key"
     */
    public static Map<String, String> getKafkaConfig(Map<String, String> systemEnv) {
        // Use treemap for ordering in logs:
        Map<String, String> config = new TreeMap<String, String>();
        for (Map.Entry<String, String> envEntry : systemEnv.entrySet()) {
            String kafkaKey = toKafkaConfigKeyOrNull(envEntry.getKey());
            if (kafkaKey == null) {
                continue;
            }
            config.put(kafkaKey, envEntry.getValue());
        }
        return config;
    }

    /**
     * Returns the dot-delimited kafka configuration string of the form "some.kafka.key", or
     * {@code null} if the string doesn't appear to be a Kafka configuration value.
     *
     * @param envName an environment variable name of the form SOME_ENV_KEY
     * @see toEnvName for the reverse operation
     */
    private static String toKafkaConfigKeyOrNull(String envName) {
        if (!envName.startsWith(KAFKA_OVERRIDE_PREFIX)) {
            return null;
        }
        // KAFKA_OVERRIDE_SOME_KAFKA_KEY => SOME_KAFKA_KEY => some_kafka_key => some.kafka.key
        return envName.substring(KAFKA_OVERRIDE_PREFIX.length()).toLowerCase().replace('_', '.');
    }
}
