package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.mesosphere.sdk.specification.validation.ValidationUtils;

import javax.validation.constraints.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Default implementation of {@link CommandSpec}.
 */
public class DefaultCommandSpec implements CommandSpec {
    @NotNull
    private String value;
    private Map<String, String> environment;

    @JsonCreator
    public DefaultCommandSpec(
            @JsonProperty("value") String value,
            @JsonProperty("environment") Map<String, String> environment) {
        this.value = value;
        this.environment = environment;
    }

    private DefaultCommandSpec(Builder builder) {
        this(builder.value, builder.getEnvironment());
    }

    /**
     * Creates a new builder instance using the provided Map for any additional config overrides.
     */
    public static Builder newBuilder(Map<String, String> envOverride) {
        return new Builder(envOverride);
    }

    public static Builder newBuilder(CommandSpec copy) {
        // Skip env override: Any overrides should already be merged into the CommandSpec's main env.
        Builder builder = newBuilder(Collections.emptyMap());
        builder.value = copy.getValue();
        builder.environment = copy.getEnvironment();
        return builder;
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * Returns the merged {@link ServiceSpec} environment plus any environment variable overrides.
     */
    @Override
    public Map<String, String> getEnvironment() {
        return environment;
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
     * {@link DefaultCommandSpec} builder.
     */
    public static final class Builder {
        private final Map<String, String> envOverride;

        private String value;
        private Map<String, String> environment;

        /**
         * Creates a new {@link Builder} with the provided Map containing override envvars.
         */
        private Builder(Map<String, String> envOverride) {
            this.envOverride = envOverride;
        }

        /**
         * Returns a combined environment containing the base environment and any overrides. The base environment is
         * provided by the developer in the {@link ServiceSpec}. The overrides are provided by the scheduler
         * environment, which may be customized in packaging without requiring a rebuild.
         */
        private Map<String, String> getEnvironment() {
            // use TreeMap for alphabetical sorting, easier diagnosis/logging:
            Map<String, String> combinedEnv = new TreeMap<>();
            if (environment != null) {
                combinedEnv.putAll(environment);
            }
            combinedEnv.putAll(envOverride);
            return combinedEnv;
        }

        /**
         * Sets the {@code value} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code value} to set
         * @return a reference to this Builder
         */
        public Builder value(String value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the {@code environment} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param environment the {@code environment} to set
         * @return a reference to this Builder
         */
        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Returns a {@code DefaultCommandSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultCommandSpec} built with parameters of this {@code DefaultCommandSpec.Builder}
         */
        public DefaultCommandSpec build() {
            DefaultCommandSpec defaultCommandSpec = new DefaultCommandSpec(this);
            ValidationUtils.validate(defaultCommandSpec);
            return defaultCommandSpec;
        }
    }
}
