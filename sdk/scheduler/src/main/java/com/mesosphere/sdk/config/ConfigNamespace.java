package com.mesosphere.sdk.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import com.google.common.collect.ImmutableMap;

/**
 * A grouping of settings which all share a given namespace, which is defined as a prefix on the
 * setting keys. Data is internally stored with the prefix(es) stripped out.
 */
public class ConfigNamespace {

    private static final ConfigNamespace EMPTY_INSTANCE =
            new ConfigNamespace(Collections.emptySet(), Collections.emptyMap());

    // Arbitrarily using TreeMap so that toString() content is in alphabetical order.
    private final Map<String, ConfigValue> config = new TreeMap<>();

    /**
     * Creates a new instance which contains the subset of {@code config} which matched the provided
     * prefix(es). The resulting config mapping will contain the subset of provided entries which
     * matched one or more of these prefixes, with the prefix content itself stripped out of the keys.
     *
     * For example, the provided input:
     *   prefixes: ["A_", "C_"]
     *   entries: {"A_ONE":"TWO", "A_THREE":"FOUR", "A_B_FIVE":"SIX", "B_SEVEN":"EIGHT", "C_NINE": "TEN"}
     * will result in the following content:
     *   config: {"ONE":"TWO", "THREE":"FOUR", "B_FIVE":"SIX", "NINE":"TEN"}
     *
     * @param prefixes to use as a filter against provided {@code config}
     * @param config to be filtered and trimmed according to provided {@code prefixes}
     */
    ConfigNamespace(Set<String> prefixes, Map<String, String> config) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            // Store the value against ALL matching prefixes. "A_B_C" should match both "A_" and "A_B_".
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    this.config.put(key.substring(prefix.length()), new ConfigValue(key, value));
                }
            }
        }
    }

    public static ConfigNamespace emptyInstance() {
        return EMPTY_INSTANCE;
    }

    /**
     * Returns a config value for the provided key within this namespace, with any prefix omitted,
     * or {@code null} if a matching value could not be found. Use {@link #getAll()} to see all
     * values.
     *
     * @param key config key of the form "SOME_KEY" with any prefix omitted
     */
    public ConfigValue get(String key) {
        return config.get(key);
    }

    /**
     * Returns an immutable copy of the full configuration within this namespace, with any prefixes
     * omitted from keys.
     */
    public ImmutableMap<String, ConfigValue> getAll() {
        return new ImmutableMap.Builder<String, ConfigValue>().putAll(config).build();
    }

    /**
     * Returns an immutable copy of the full configuration within this namespace, as string values.
     */
    public ImmutableMap<String, String> getAllEnv() {
        ImmutableMap.Builder<String, String> mapBuilder = new ImmutableMap.Builder<>();
        for (Map.Entry<String, ConfigValue> entry : config.entrySet()) {
            mapBuilder.put(entry.getKey(), entry.getValue().requiredString());
        }
        return mapBuilder.build();
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
