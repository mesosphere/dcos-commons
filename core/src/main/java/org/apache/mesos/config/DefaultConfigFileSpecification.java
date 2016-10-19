package org.apache.mesos.config;

import org.apache.mesos.specification.ConfigFileSpecification;

/**
 * Basic implementation of {@link ConfigFileSpecification} which returns the provided values.
 */
public class DefaultConfigFileSpecification implements ConfigFileSpecification {

    private final String relativePath;
    private final String templateContent;

    public DefaultConfigFileSpecification(String relativePath, String templateContent) {
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
}
