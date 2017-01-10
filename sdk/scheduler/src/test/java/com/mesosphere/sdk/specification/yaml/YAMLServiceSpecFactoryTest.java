package com.mesosphere.sdk.specification.yaml;

import org.apache.commons.io.FileUtils;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.*;
import static org.mockito.Mockito.when;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

public class YAMLServiceSpecFactoryTest {
    @Rule public final EnvironmentVariables environmentVariables = OfferRequirementTestUtils.getApiPortEnvironment();
    @Mock private FileReader mockFileReader;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGenerateSpecFromYAML() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), mockFileReader);
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(TestConstants.PORT_API_VALUE, Integer.valueOf(serviceSpec.getApiPort()));
    }

    @Test
    public void testGenerateRawSpecFromYAMLFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        Assert.assertNotNull(rawServiceSpec);
        Assert.assertEquals(TestConstants.PORT_API_VALUE, rawServiceSpec.getScheduler().getApiPort());
    }

    @Test
    public void testGenerateRawSpecFromYAMLString() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        String yaml = FileUtils.readFileToString(file);
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(yaml);
        Assert.assertNotNull(rawServiceSpec);
        Assert.assertEquals(TestConstants.PORT_API_VALUE, rawServiceSpec.getScheduler().getApiPort());
    }
}
