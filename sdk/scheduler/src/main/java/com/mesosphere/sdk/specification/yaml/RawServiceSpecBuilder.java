package com.mesosphere.sdk.specification.yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;

/**
 * Handles rendering of {@link RawServiceSpec}s based on the Scheduler's environment variables.
 */
public class RawServiceSpecBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawServiceSpecBuilder.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    static {
        // If the user provides duplicate fields (e.g. 'count' twice), throw an error instead of silently dropping data:
        YAML_MAPPER.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    private final String yamlTemplate;
    private Map<String, String> env;

    public RawServiceSpecBuilder(File pathToYamlTemplate) throws IOException {
        this(FileUtils.readFileToString(pathToYamlTemplate));
    }

    public RawServiceSpecBuilder(String yamlTemplate) {
        this.yamlTemplate = yamlTemplate;
        this.env = System.getenv();
    }

    /**
     * Overrides use of the scheduler's environment variables with the provided custom map.
     */
    @VisibleForTesting
    public RawServiceSpecBuilder setEnv(Map<String, String> env) {
        this.env = env;
        return this;
    }

    /**
     * Provides the provided yaml template as rendered against either the provided environment variables or against the
     * provided environment.
     */
    public RawServiceSpec build() throws Exception {
        String yamlWithEnv = TemplateUtils.applyEnvToMustache(yamlTemplate, env);
        LOGGER.info("Rendered ServiceSpec:\n{}", yamlWithEnv);
        if (!TemplateUtils.isMustacheFullyRendered(yamlWithEnv)) {
            throw new IllegalStateException("YAML contains unsubstitued variables.");
        }
        return YAML_MAPPER.readValue(yamlWithEnv.getBytes(StandardCharsets.UTF_8), RawServiceSpec.class);
    }
}
