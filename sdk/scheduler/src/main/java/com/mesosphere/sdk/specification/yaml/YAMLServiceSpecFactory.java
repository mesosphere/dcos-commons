package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.io.FileUtils;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
            return FileUtils.readFileToString(new File(path), "UTF-8");
        }
    }

    public static final RawServiceSpec generateRawSpecFromYAML(File pathToYaml) throws Exception {
        return generateRawSpecFromYAML(FileUtils.readFileToString(pathToYaml, CHARSET));
    }

    public static final RawServiceSpec generateRawSpecFromYAML(final String yaml) throws Exception {
        final String yamlWithEnv = CommonTaskUtils.applyEnvToMustache(yaml, System.getenv());
        LOGGER.info("Rendered ServiceSpec:\n{}", yamlWithEnv);
        if (!CommonTaskUtils.isMustacheFullyRendered(yamlWithEnv)) {
            throw new IllegalStateException("YAML contains unsubstitued variables.");
        }
        return YAML_MAPPER.readValue(yamlWithEnv.getBytes(CHARSET), RawServiceSpec.class);
    }

    /**
     * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}.
     *
     * @param rawServiceSpec the raw service specification representing a YAML file
     * @throws Exception if the conversion fails
     */
    public static final DefaultServiceSpec generateServiceSpec(RawServiceSpec rawServiceSpec) throws Exception {
        return generateServiceSpec(rawServiceSpec, new FileReader());
    }

    /**
     * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}. This version allows
     * providing a custom file reader for use in testing.
     *
     * @param rawServiceSpec the raw service specification representing a YAML file
     * @param fileReader the file reader to be used for reading template files, allowing overrides for testing
     * @throws Exception if the conversion fails
     */
    @VisibleForTesting
    public static final DefaultServiceSpec generateServiceSpec(
            RawServiceSpec rawServiceSpec, FileReader fileReader) throws Exception {
        return YAMLToInternalMappers.from(rawServiceSpec, fileReader);
    }
}
