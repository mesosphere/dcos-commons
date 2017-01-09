package com.mesosphere.sdk.specification.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Generates {@link ServiceSpec} from a given YAML definition.
 */
public class YAMLServiceSpecFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(YAMLServiceSpecFactory.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Charset CHARSET = StandardCharsets.UTF_8;

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

    public static final DefaultServiceSpec generateServiceSpec(RawServiceSpec rawServiceSpec)
            throws Exception {
        return YAMLToInternalMappers.from(rawServiceSpec);
    }
}
