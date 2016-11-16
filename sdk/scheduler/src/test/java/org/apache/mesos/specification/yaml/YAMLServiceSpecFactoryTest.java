package org.apache.mesos.specification.yaml;

import org.apache.commons.io.FileUtils;
import org.apache.mesos.specification.DefaultServiceSpec;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;

public class YAMLServiceSpecFactoryTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testGenerateSpecFromYAML() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test.yml.mustache").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateSpecFromYAML(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
    }

    @Test
    public void testGenerateRawSpecFromYAMLFile() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test.yml.mustache").getFile());
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        Assert.assertNotNull(rawServiceSpecification);
        Assert.assertEquals(new Integer(8080), rawServiceSpecification.getApiPort());
    }

    @Test
    public void testGenerateRawSpecFromYAMLString() throws Exception {
        environmentVariables.set("PORT0", "8080");
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test.yml.mustache").getFile());
        String yaml = FileUtils.readFileToString(file);
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(yaml);
        Assert.assertNotNull(rawServiceSpecification);
        Assert.assertEquals(new Integer(8080), rawServiceSpecification.getApiPort());
    }
}
