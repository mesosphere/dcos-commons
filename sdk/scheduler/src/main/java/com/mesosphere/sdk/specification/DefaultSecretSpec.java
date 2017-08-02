package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.*;

/**
 * Default implementation of {@link SecretSpec}.
 */
public class DefaultSecretSpec implements SecretSpec {
    @NotNull
    @Size(min = 1)
    private final String secretPath;

    private final String envKey;

    /** Regexp in @Pattern:
     *      sub-pattern = [.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*
     *      (sub-pattern)?  = either NULL, or sub-pattern.  So It can be Null.
     *      No leading slash character is allowed!
     */
    @Pattern(regexp = "([.a-zA-Z0-9]+([.a-zA-Z0-9_-]*[/\\\\]*)*)?")
    private final String filePath;

    @JsonCreator
    public DefaultSecretSpec(
            @JsonProperty("secret") String secretPath,
            @JsonProperty("env-key") String envKey,
            @JsonProperty("file") String filePath) {
        this.secretPath = secretPath;
        this.envKey = envKey;
        this.filePath = filePath;
        ValidationUtils.validate(this);
    }

    private DefaultSecretSpec(Builder builder) {
        this(builder.secretPath, builder.envKey, builder.filePath);
        ValidationUtils.validate(this);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @JsonProperty("secret")
    @Override
    public String getSecretPath() {
        return secretPath;
    }

    @JsonProperty("env-key")
    @Override
    public Optional<String> getEnvKey() {
        return Optional.ofNullable(envKey);
    }

    @JsonProperty("file")
    @Override
    public Optional<String> getFilePath() {
            return Optional.ofNullable(filePath);
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
        public Builder filePath(String filePath) {
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
