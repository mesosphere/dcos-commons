package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link CommandSpec}.
 */
public class DefaultCommandSpec implements CommandSpec {
    private String value;
    private Map<String, String> environment;
    private String user;
    private Collection<URI> uris;

    @JsonCreator
    public DefaultCommandSpec(
            @JsonProperty("value") String value,
            @JsonProperty("environment") Map<String, String> environment,
            @JsonProperty("user") String user,
            @JsonProperty("uris") Collection<URI> uris) {
        this.value = value;
        this.environment = environment;
        this.user = user;
        this.uris = uris;
    }

    private DefaultCommandSpec(Builder builder) {
        this(
                builder.value,
                builder.environment,
                builder.user,
                builder.uris);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(DefaultCommandSpec copy) {
        Builder builder = new Builder();
        builder.value = copy.value;
        builder.environment = copy.environment;
        builder.user = copy.user;
        builder.uris = copy.uris;
        return builder;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Map<String, String> getEnvironment() {
        return environment;
    }

    @Override
    public Optional<String> getUser() {
        return Optional.ofNullable(user);
    }

    @Override
    public Collection<URI> getUris() {
        return uris;
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
     * {@code DefaultCommandSpec} builder static inner class.
     */
    public static final class Builder {
        private String value;
        private Map<String, String> environment;
        private String user;
        private Collection<URI> uris;

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
         * Sets the {@code environment} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param environment the {@code environment} to set
         * @return a reference to this Builder
         */
        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets the {@code user} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param user the {@code user} to set
         * @return a reference to this Builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the {@code uris} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param uris the {@code uris} to set
         * @return a reference to this Builder
         */
        public Builder uris(Collection<URI> uris) {
            this.uris = uris;
            return this;
        }

        /**
         * Returns a {@code DefaultCommandSpec} built from the parameters previously set.
         *
         * @return a {@code DefaultCommandSpec} built with parameters of this {@code DefaultCommandSpec.Builder}
         */
        public DefaultCommandSpec build() {
            return new DefaultCommandSpec(this);
        }
    }
}
