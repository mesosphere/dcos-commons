package com.mesosphere.sdk.specification.yaml;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * Tests for {@link TemplateUtils}.
 */
public class TemplateUtilsTest {
    @Rule
    public final EnvironmentVariables environmentVariables = OfferRequirementTestUtils.getApiPortEnvironment();

    @Test
    public void testApplyEnvToMustache() throws IOException {
        environmentVariables.set("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        String yaml = FileUtils.readFileToString(file);
        Assert.assertTrue(yaml.contains("api-port: {{PORT_API}}"));
        String renderedYaml = TemplateUtils.applyEnvToMustache(yaml, System.getenv());
        Assert.assertTrue(renderedYaml.contains(String.format("api-port: %d", TestConstants.PORT_API_VALUE)));
    }
}
