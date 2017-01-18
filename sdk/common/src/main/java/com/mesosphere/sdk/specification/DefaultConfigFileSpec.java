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
    public DefaultConfigFileSpec(
            @JsonProperty("name") String name,
            @JsonProperty("relative-path") String relativePath,
            @JsonProperty("template-content") String templateContent) {
        this.name = name;
        this.relativePath = relativePath;
        this.templateContent = templateContent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRelativePath() {
        return relativePath;
    }

    @Override
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
}
