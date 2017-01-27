package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;

import java.io.File;

/**
 * Test utils for {@code {@link com.mesosphere.sdk.specification.ServiceSpec}}.
 */
public class ServiceSpecTestUtils {

    public static DefaultServiceSpec getPodInstance(String serviceSpecFileName) throws Exception {
        return getPodInstance(serviceSpecFileName, new YAMLServiceSpecFactory.FileReader());
    }

    public static DefaultServiceSpec getPodInstance(
            String serviceSpecFileName, YAMLServiceSpecFactory.FileReader fileReader) throws Exception {
        ClassLoader classLoader = ServiceSpecTestUtils.class.getClassLoader();
        File file = new File(classLoader.getResource(serviceSpecFileName).getFile());
        return YAMLServiceSpecFactory.generateServiceSpec(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file), fileReader);
    }
}
