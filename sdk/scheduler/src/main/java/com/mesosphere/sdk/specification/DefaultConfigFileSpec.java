package com.mesosphere.sdk.specification;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Basic implementation of {@link ConfigFileSpec} which returns the provided values.
 */
public class DefaultConfigFileSpec implements ConfigFileSpec {

    private final String name;
    private final String relativePath;
    private final String templateContent;

    @JsonCreator
    private DefaultConfigFileSpec(
            @JsonProperty("name") String name,
            @JsonProperty("relative-path") String relativePath,
            @JsonProperty("template-content") String templateContent) {
        this.name = name;
        this.relativePath = relativePath;
        this.templateContent = templateContent;
    }

    private DefaultConfigFileSpec(Builder builder) {
        this(builder.name, builder.relativePath, builder.templateContent);
        ValidationUtils.nonEmpty(this, "name", name);
        ValidationUtils.nonEmpty(this, "relativePath", relativePath);
        ValidationUtils.nonEmpty(this, "templateContent", templateContent);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @Override
    @JsonProperty("relative-path")
    public String getRelativePath() {
        return relativePath;
    }

    @Override
    @JsonProperty("template-content")
    public String getTemplateContent() {
        return templateContent;
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
     * {@link DefaultConfigFileSpec} builder static inner class.
     */
    public static class Builder {
        private String name;
        private String relativePath;
        private String templateContent;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath;
            return this;
        }

        public Builder templateContent(String templateContent) {
            this.templateContent = templateContent;
            return this;
        }

        public DefaultConfigFileSpec build() {
            return new DefaultConfigFileSpec(this);
        }
    }
}
