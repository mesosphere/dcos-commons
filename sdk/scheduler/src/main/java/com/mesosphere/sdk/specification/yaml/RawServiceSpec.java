package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root of the parsed YAML object model.
 */
public class RawServiceSpec {

    /**
     * Handles rendering of {@link RawServiceSpec}s based on the Scheduler's environment variables.
     */
    public static class Builder {

        private static final Logger LOGGER = LoggerFactory.getLogger(Builder.class);
        private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
        static {
            // If the user provides duplicate fields (e.g. 'count' twice), throw an error instead of silently dropping
            // data:
            YAML_MAPPER.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        }

        private final File pathToYamlTemplate;
        private Map<String, String> env;

        private Builder(File pathToYamlTemplate) {
            this.pathToYamlTemplate = pathToYamlTemplate;
            this.env = System.getenv();
        }

        /**
         * Overrides use of the scheduler's environment variables with the provided custom map.
         */
        @VisibleForTesting
        public Builder setEnv(Map<String, String> env) {
            this.env = env;
            return this;
        }

        /**
         * Returns the object representation of the provided template data, with any mustache templating resolved using
         * the provided environment map.
         */
        public RawServiceSpec build() throws Exception {
            // We allow missing values. For example, the service principal may be left empty, in which case we use a
            // reasonable default principal.
            List<TemplateUtils.MissingValue> missingValues = new ArrayList<>();
            String yamlWithEnv = TemplateUtils.renderMustache(
                    pathToYamlTemplate.getName(),
                    FileUtils.readFileToString(pathToYamlTemplate, StandardCharsets.UTF_8),
                    env,
                    missingValues);
            LOGGER.info("Rendered ServiceSpec from {}:\nMissing template values: {}\n{}",
                    pathToYamlTemplate.getAbsolutePath(), missingValues, yamlWithEnv);
            return YAML_MAPPER.readValue(yamlWithEnv.getBytes(StandardCharsets.UTF_8), RawServiceSpec.class);
        }
    }

    private final String name;
    private final String webUrl;
    private final RawScheduler scheduler;
    private final WriteOnceLinkedHashMap<String, RawPod> pods;
    private final WriteOnceLinkedHashMap<String, RawPlan> plans;

    public static Builder newBuilder(File pathToYamlTemplate) throws IOException {
        return new Builder(pathToYamlTemplate);
    }

    @JsonCreator
    private RawServiceSpec(
            @JsonProperty("name") String name,
            @JsonProperty("web-url") String webUrl,
            @JsonProperty("scheduler") RawScheduler scheduler,
            @JsonProperty("pods") WriteOnceLinkedHashMap<String, RawPod> pods,
            @JsonProperty("plans") WriteOnceLinkedHashMap<String, RawPlan> plans) {
        this.name = name;
        this.webUrl = webUrl;
        this.scheduler = scheduler;
        this.pods = pods;
        this.plans = plans;
    }

    public String getName() {
        return name;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public RawScheduler getScheduler() {
        return scheduler;
    }

    public LinkedHashMap<String, RawPod> getPods() {
        return pods;
    }

    public WriteOnceLinkedHashMap<String, RawPlan> getPlans() {
        return plans;
    }
}
