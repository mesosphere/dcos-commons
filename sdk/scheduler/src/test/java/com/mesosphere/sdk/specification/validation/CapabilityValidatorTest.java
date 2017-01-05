package com.mesosphere.sdk.specification.validation;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.*;
import static org.mockito.Mockito.when;

/**
 * This class tests {@link CapabilityValidator}.
 */
public class CapabilityValidatorTest {
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Mock private Capabilities mockCapabilities;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        environmentVariables.set("PORT0", "8080");
    }

    @Test
    public void testSpecSucceedsWithoutRLimits() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(false);
        CapabilityValidator capabilityValidator = new CapabilityValidator(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));

        capabilityValidator.validate(serviceSpec);
    }

    @Test
    public void testSpecSucceedsWithRLimits() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(true);
        CapabilityValidator capabilityValidator = new CapabilityValidator(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));

        capabilityValidator.validate(serviceSpec);
    }

    @Test(expected = CapabilityValidator.CapabilityValidationException.class)
    public void testSpecFailsWithRLimitsButWithoutCapability() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(false);
        CapabilityValidator capabilityValidator = new CapabilityValidator(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));

        capabilityValidator.validate(serviceSpec);
    }
}
