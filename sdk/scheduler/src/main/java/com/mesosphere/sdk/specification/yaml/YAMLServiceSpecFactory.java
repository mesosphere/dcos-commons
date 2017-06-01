package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.io.FileUtils;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

/**
 * Generates {@link ServiceSpec} from a given YAML definition.
 */
public class YAMLServiceSpecFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(YAMLServiceSpecFactory.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    static {
        // If the user provides duplicate fields (e.g. 'count' twice), throw an error instead of silently dropping data:
        YAML_MAPPER.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Implementation for reading files from disk. Meant to be overridden by a mock in tests.
     */
    @VisibleForTesting
    public static class FileReader {
        public String read(String path) throws IOException {
            return FileUtils.readFileToString(new File(path), CHARSET);
        }
    }

    public static final RawServiceSpec generateRawSpecFromYAML(File pathToYaml) throws Exception {
        return generateRawSpecFromYAML(pathToYaml, System.getenv());
    }

    public static final RawServiceSpec generateRawSpecFromYAML(File pathToYaml, Map<String, String> env)
            throws Exception {
        return generateRawSpecFromYAML(FileUtils.readFileToString(pathToYaml, CHARSET), env);
    }

    public static final RawServiceSpec generateRawSpecFromYAML(final String yaml) throws Exception {
        return generateRawSpecFromYAML(yaml, System.getenv());
    }

    public static final RawServiceSpec generateRawSpecFromYAML(final String yaml, Map<String, String> env)
            throws Exception {
        HashMap<String, Object> scopes = new HashMap<String, Object>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith("JSONPARSE_")) {
                LOGGER.info("JSONPARSE value:\n{}", value);
                String trimmedValue = value.trim();
                switch (value.trim().charAt(0)) {
                    case '{':
                        scopes.put(key, parseJsonObject(value));
                        continue;
                    case '[':
                        scopes.put(key, parseJsonArray(value));
                        continue;
                }
                throw new IllegalStateException(String.format("Could not parse JSON: %s", value));
            }
            scopes.put(key, value);
        }

        final String yamlWithEnv = TemplateUtils.applyEnvToMustache(yaml, scopes);
        LOGGER.info("Rendered ServiceSpec:\n{}", yamlWithEnv);
        if (!TemplateUtils.isMustacheFullyRendered(yamlWithEnv)) {
            throw new IllegalStateException("YAML contains unsubstitued variables.");
        }
        return YAML_MAPPER.readValue(yamlWithEnv.getBytes(CHARSET), RawServiceSpec.class);
    }

    static HashMap<String, Object> parseJsonObject(String str) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream input = new ByteArrayInputStream(str.getBytes("UTF-8"));
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
        return mapper.readValue(input, typeRef);
    }

    static List<Object> parseJsonArray(String str) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream input = new ByteArrayInputStream(str.getBytes("UTF-8"));
        TypeReference<List<Object>> typeRef = new TypeReference<List<Object>>() {};
        return mapper.readValue(input, typeRef);
    }

    /**
     * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}.
     *
     * @param rawServiceSpec the raw service specification representing a YAML file
     * @param schedulerFlags scheduler flags to use when building the service spec
     * @throws Exception if the conversion fails
     */
    public static final DefaultServiceSpec generateServiceSpec(
            RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) throws Exception {
        return generateServiceSpec(rawServiceSpec, schedulerFlags, new FileReader());
    }

    /**
     * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}. This version allows
     * providing a custom file reader for use in testing.
     *
     * @param rawServiceSpec the raw service specification representing a YAML file
     * @param schedulerFlags scheduler flags to use when building the service spec
     * @param fileReader the file reader to be used for reading template files, allowing overrides for testing
     * @throws Exception if the conversion fails
     */
    @VisibleForTesting
    public static final DefaultServiceSpec generateServiceSpec(
            RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags, FileReader fileReader) throws Exception {
        return YAMLToInternalMappers.from(rawServiceSpec, schedulerFlags, fileReader);
    }
}
