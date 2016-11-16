package org.apache.mesos.specification.yaml;

import org.apache.commons.io.FileUtils;
import org.apache.mesos.specification.DefaultServiceSpec;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;

import static org.apache.mesos.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;

public class YAMLServiceSpecFactoryTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testGenerateSpecFromYAML() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateSpecFromYAML(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
    }

    @Test
    public void testGenerateRawSpecFromYAMLFile() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        RawServiceSpecification rawServiceSpecification = generateRawSpecFromYAML(file);
        Assert.assertNotNull(rawServiceSpecification);
        Assert.assertEquals(new Integer(8080), rawServiceSpecification.getApiPort());
    }

    @Test
    public void testGenerateRawSpecFromYAMLString() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        String yaml = FileUtils.readFileToString(file);
        RawServiceSpecification rawServiceSpecification = generateRawSpecFromYAML(yaml);
        Assert.assertNotNull(rawServiceSpecification);
        Assert.assertEquals(new Integer(8080), rawServiceSpecification.getApiPort());
    }
}
