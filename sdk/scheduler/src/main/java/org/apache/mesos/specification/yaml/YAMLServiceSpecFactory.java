package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import org.apache.mesos.specification.ServiceSpec;

import java.io.File;
import java.nio.charset.Charset;

/**
 * Generates {@link ServiceSpec} from a given YAML definition.
 */
public class YAMLServiceSpecFactory {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static final ServiceSpec generateFromYAML(File pathToYaml) throws Exception {
        return generateFromYAML(FileUtils.readFileToString(pathToYaml, Charset.forName("UTF-8")));
    }

    public static final ServiceSpec generateFromYAML(String yaml) throws Exception {
        return YAMLToInternalMappers.from(YAML_MAPPER.readValue(yaml.getBytes(), RawServiceSpecification.class));
    }
}
