package org.apache.mesos.specification;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Basic implementation of {@link ConfigFileSpecification} which returns the provided values.
 */
public class DefaultConfigFileSpecification implements ConfigFileSpecification {

    private final String relativePath;
    private final String templateContent;

    @JsonCreator
    public DefaultConfigFileSpecification(
            @JsonProperty("relative_path") String relativePath,
            @JsonProperty("template_content") String templateContent) {
        this.relativePath = relativePath;
        this.templateContent = templateContent;
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
