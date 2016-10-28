package org.apache.mesos.config;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.Environment;

import com.google.common.collect.ImmutableMap;

/**
 * A grouping of settings which all share a given namespace, which is defined as a prefix on the
 * setting keys. Data is internally stored with the prefix(es) stripped out.
 */
public class ConfigNamespace {

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
     * Returns a new {@link CommandInfo} whose environment variables includes any values from this
     * instance. Any matching entries already present in the provided {@link CommandInfo} will be
     * overwritten.
     */
    public CommandInfo updateEnvironment(CommandInfo command) {
        Environment.Builder envBuilder = command.getEnvironment().toBuilder();
        for (Map.Entry<String, ConfigValue> entry : config.entrySet()) {
            envBuilder = withEntrySet(envBuilder, entry.getKey(), entry.getValue().requiredString());
        }
        if (!command.hasEnvironment() && envBuilder.getVariablesCount() == 0) {
            // Avoid changing the CommandInfo if we don't have anything to add: avoid creating false
            // positives for changes to the task. If environment was unset before, avoid populating
            // an empty environment list. This should really only come up in tests, but good to
            // check regardless.
            return command;
        }
        return command.toBuilder().setEnvironment(envBuilder).build();
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

    /**
     * Returns a new {@link Environment.Builder} with the provided entry set. Any matching entries
     * already present will have been removed before adding the new entry to the end.
     */
    private static Environment.Builder withEntrySet(
            Environment.Builder envBuilder, String name, String value) {
        // Remove matching entry/entries, if any:
        Environment.Builder filteredEnvBuilder = Environment.newBuilder();
        for (Environment.Variable entry : envBuilder.getVariablesList()) {
            if (!entry.getName().equals(name)) {
                filteredEnvBuilder.addVariables(entry);
            }
        }
        // Append new entry to end:
        filteredEnvBuilder.addVariablesBuilder()
                .setName(name)
                .setValue(value);
        return filteredEnvBuilder;
    }
}
