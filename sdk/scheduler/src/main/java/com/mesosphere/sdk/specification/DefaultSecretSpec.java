package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.StringMatcher;
import com.mesosphere.sdk.specification.util.RLimit;
import com.mesosphere.sdk.specification.validation.UniqueTaskName;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.net.URI;
import java.util.*;

/**
 * Default implementation of {@link SecretSpec}.
 */
public class DefaultSecretSpec implements SecretSpec {
    @NotNull
    @Size(min = 1)
    private final String secretPath;
    @Valid
    private final String envKey;
    @NotNull
    @Size(min = 1)
    private final String filePath;

    @JsonCreator
    public DefaultSecretSpec(
            @JsonProperty("secret") String secretPath,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("file") String filePath) {
        this.secretPath = secretPath;
        this.envKey = envKey;
        this.filePath = filePath;
    }

    private DefaultSecretSpec(Builder builder) {
        this(builder.secretPath, builder.envKey, builder.filePath);
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public String getSecretPath() {
        return secretPath;
    }

    @Override
    public Optional<String> getEnvKey() {
        return Optional.ofNullable(envKey);
    }

    @Override
    public String getFilePath() {
        return filePath;
    }

    /**
     * {@code DefaultSecretSpec} builder static inner class.
     */
    public static final class Builder {

        private String secretPath;
        private String envKey;
        private String filePath;

        private Builder() {
        }

        /**
         * Sets the {@code secretPath} and returns a reference to this Builder.
         *
         * @param secretPath the {@code secretPath} to set
         * @return a reference to this Builder
         */
        public Builder secretPath(String secretPath) {
            this.secretPath = secretPath;
            return this;
        }

        /**
         * Sets the {@code envKey} and returns a reference to this Builder.
         *
         * @param envKey the {@code envKey} to set
         * @return a reference to this Builder
         */
        public Builder envKey(String envKey) {
            this.envKey = envKey;
            return this;
        }

        /**
         * Sets the {@code filePath} and returns a reference to this Builder.
         *
         * @param filePath the {@code filePath} to set
         * @return a reference to this Builder
         */
        public Builder count(String filePath) {
            this.filePath = filePath;
            return this;
        }

        /**
         * Returns a {@code DefaultSecretSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultSecretSpec} built with parameters of this {@code DefaultSecretSpec.Builder}
         */
        public DefaultSecretSpec build() {
            DefaultSecretSpec defaultSecretSpec = new DefaultSecretSpec(this);
            return defaultSecretSpec;
        }
    }
}
