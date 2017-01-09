package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Optional;

/**
 * Default implementation of {@link UriSpec}.
 */
public class DefaultUriSpec implements UriSpec {
    @NotNull
    private final String value;
    private final boolean isExecutable;
    private final boolean shouldExtract;
    private final boolean shouldCache;
    private final String outputFile;

    @JsonCreator
    public DefaultUriSpec(
            @JsonProperty("value") String value,
            @JsonProperty("executable") boolean isExecutable,
            @JsonProperty("extract") boolean shouldExtract,
            @JsonProperty("cache") boolean shouldCache,
            @JsonProperty("output-file") String outputFile) {
        this.value = value;
        this.isExecutable = isExecutable;
        this.shouldExtract = shouldExtract;
        this.shouldCache = shouldCache;
        this.outputFile = outputFile;
        ValidationUtils.validate(this);
    }

    public DefaultUriSpec(String value) {
        this(value, false, true, true, null);
    }

    public DefaultUriSpec(URI uri) {
        this(uri.toString());
    }

    public DefaultUriSpec(Builder builder) {
        this(
                builder.value,
                builder.isExecutable,
                builder.shouldExtract,
                builder.shouldCache,
                builder.outputFile);
    }

    @Override
    @JsonProperty("value")
    public String getValue() {
        return value;
    }

    @Override
    @JsonProperty("executable")
    public boolean isExecutable() {
        return isExecutable;
    }

    @Override
    @JsonProperty("extract")
    public boolean shouldExtract() {
        return shouldExtract;
    }

    @Override
    @JsonProperty("cache")
    public boolean shouldCache() {
        return shouldCache;
    }

    @Override
    @JsonProperty("output-file")
    public Optional<String> getOutputFile() {
        return Optional.ofNullable(outputFile);
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
     * {@code DefaultUriSpec} builder static inner class.
     */
    public static final class Builder {
        private String value;
        private boolean isExecutable;
        private boolean shouldExtract;
        private boolean shouldCache;
        private String outputFile;

        private Builder() {
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
         * Sets {@code executable} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param isExecutable sets a flag to indicate the content of the URI is executable
         * @return a reference to this Builder
         */
        public Builder executable(boolean isExecutable) {
            this.isExecutable = isExecutable;
            return this;
        }

        /**
         * Sets {@code extractable} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param shouldExtract sets a flag to indicate the content of the URI should be extracted
         * @return a reference to this Builder
         */
        public Builder extract(boolean shouldExtract) {
            this.shouldExtract = shouldExtract;
            return this;
        }

        /**
         * Sets {@code extractable} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param shouldCache sets a flag to indicate the content of the URI should be extracted
         * @return a reference to this Builder
         */
        public Builder cache(boolean shouldCache) {
            this.shouldCache = shouldCache;
            return this;
        }

        /**
         * Sets the {@code outputFile} and returns a reference to this Builder so that the methods can be chained
         * together.
         *
         * @param outputFile the {@code outputFile} to set
         * @return a reference to this Builder
         */
        public Builder outputFile(String outputFile) {
            this.outputFile = outputFile;
            return this;
        }


        /**
         * Returns a {@code DefaultResourceSpecification} built from the parameters previously set.
         *
         * @return a {@code DefaultResourceSpecification} built with parameters of this
         * {@code DefaultResourceSpecification.Builder}
         */
        public DefaultUriSpec build() {
            return new DefaultUriSpec(this);
        }
    }
}
