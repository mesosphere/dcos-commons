package com.mesosphere.sdk.specification.validation;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.MockDcosCluster;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;

/**
 * This class tests {@link CapabilityValidator}.
 */
public class CapabilityValidatorTest {
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();
    private MockDcosCluster mockDcosCluster;

    @Before
    public void beforeEach() {
        environmentVariables.set("PORT0", "8080");
    }

    @After
    public void afterEach() {
        if (mockDcosCluster != null) {
            mockDcosCluster.stop();
        }
    }

    @Test
    public void testSpecSucceedsWithoutRLimits() throws Exception {
        mockDcosCluster = MockDcosCluster.create("1.8.0");
        CapabilityValidator capabilityValidator = new CapabilityValidator(
                new Capabilities(mockDcosCluster.getDcosCluster()));

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(generateRawSpecFromYAML(file));

        capabilityValidator.validate(serviceSpec);

    }

    @Test
    public void testSpecSucceedsWithRLimitsAnd1_9Cluster() throws Exception {
        mockDcosCluster = MockDcosCluster.create("1.9.0");
        CapabilityValidator capabilityValidator = new CapabilityValidator(
                new Capabilities(mockDcosCluster.getDcosCluster()));

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(generateRawSpecFromYAML(file));

        capabilityValidator.validate(serviceSpec);
    }

    @Test(expected = CapabilityValidator.CapabilityValidationException.class)
    public void testSpecFailsWithRLimitsAnd1_8Cluster() throws Exception {
        mockDcosCluster = MockDcosCluster.create("1.8.0");
        CapabilityValidator capabilityValidator = new CapabilityValidator(
                new Capabilities(mockDcosCluster.getDcosCluster()));

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(generateRawSpecFromYAML(file));

        capabilityValidator.validate(serviceSpec);
    }
}