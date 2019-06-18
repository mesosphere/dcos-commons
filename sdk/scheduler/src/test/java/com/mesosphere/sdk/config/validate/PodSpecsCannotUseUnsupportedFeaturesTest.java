package com.mesosphere.sdk.config.validate;

import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLToInternalMappers;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;

/**
 * Tests for {@link PodSpecsCannotUseUnsupportedFeatures}.
 */
public class PodSpecsCannotUseUnsupportedFeaturesTest {
    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();
    private static final PodSpecsCannotUseUnsupportedFeatures VALIDATOR = new PodSpecsCannotUseUnsupportedFeatures();

    @Mock private Capabilities mockCapabilities;
    @Mock private YAMLToInternalMappers.ConfigTemplateReader mockConfigTemplateReader;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSpecSucceedsWithoutSeccomp() throws Exception {
        when(mockCapabilities.supportsSeccomp()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();

        checkValidationPasses(serviceSpec);
    }

    @Test
    public void testSpecsSucceedsWithSeccomp() throws Exception {
        when(mockCapabilities.supportsSeccomp()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();

        checkValidationPasses(serviceSpec);
    }

    @Test
    public void testSpecFailsWithSeccompUnconfinedButWithoutCapability() throws Exception {
        when(mockCapabilities.supportsSeccomp()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("seccomp-unconfined.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();

        checkValidationErrorWithValue(serviceSpec, "seccomp");
    }

    @Test
    public void testSpecFailsWithSeccompProfileNameButWithoutCapability() throws Exception {
        when(mockCapabilities.supportsSeccomp()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("seccomp-profile-name.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();

        checkValidationErrorWithValue(serviceSpec, "seccomp");
    }

    @Test
    public void testSpecSuceedsWithSeccompProfileNameAndCapability() throws Exception {
        when(mockCapabilities.supportsSeccomp()).thenReturn(true);

        Capabilities.overrideCapabilities(mockCapabilities);
        File file = new File(getClass().getClassLoader().getResource("seccomp-profile-name.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();

        checkValidationPasses(serviceSpec);
    }

    @Test
    public void testSpecSuceedsWithSeccompProfileUnconfinedAndCapability() throws Exception {
        when(mockCapabilities.supportsSeccomp()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("seccomp-unconfined.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();
        checkValidationPasses(serviceSpec);
    }


    @Test
    public void testSpecSucceedsWithoutRLimits() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(false);
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();

        checkValidationPasses(serviceSpec);
    }

    @Test
    public void testSpecSucceedsWithRLimits() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(true);
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        when(mockCapabilities.supportsCNINetworking()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        when(mockConfigTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockConfigTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockConfigTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();

        checkValidationPasses(serviceSpec);
    }

    @Test
    public void testSpecFailsWithRLimitsButWithoutCapability() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(false);
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        when(mockConfigTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockConfigTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockConfigTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationErrorWithValue(serviceSpec, "rlimits");
    }

    @Test
    public void testSpecFailsWhenGpuResourceNotSupported() throws Exception {
        when(mockCapabilities.supportsGpuResource()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);

        when(mockConfigTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockConfigTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockConfigTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        // check that it works when GPUs are specified at the task level
        File file = new File(getClass().getClassLoader().getResource("valid-gpu-resource.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationErrorWithValue(serviceSpec, "gpus");

        // check that it works when GPUs are specified at the resourceSet level
        file = new File(getClass().getClassLoader().getResource("valid-gpu-resourceset.yml").getFile());
        serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationErrorWithValue(serviceSpec, "gpus");
    }

    @Test
    public void testSpecSucceedsWhenGpuResourceIsSupported() throws Exception {
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        when(mockConfigTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockConfigTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockConfigTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(getClass().getClassLoader().getResource("valid-gpu-resource.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationPasses(serviceSpec);

        when(mockCapabilities.supportsRLimits()).thenReturn(true);
        when(mockCapabilities.supportsCNINetworking()).thenReturn(true);
        File file2 = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        serviceSpec = DefaultServiceSpec.newGenerator(file2, SCHEDULER_CONFIG).setConfigTemplateReader(mockConfigTemplateReader).build();
        checkValidationPasses(serviceSpec);
    }

    @Test
    public void testSpecFailsWhenCniPortMappingIsNotSupported() throws Exception {
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        when(mockCapabilities.supportsRLimits()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        when(mockConfigTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockConfigTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockConfigTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file2 = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file2, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationErrorWithValue(serviceSpec, "network");
    }

    @Test
    public void testSpecFailsWhenFileSecretIsNotSupported() throws Exception {
        when(mockCapabilities.supportsFileBasedSecrets()).thenReturn(false);
        when(mockCapabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-secrets.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationErrorWithValue(serviceSpec, "secrets:file");
    }

    @Test
    public void testSpecSucceedsWhenSecretIsSupported() throws Exception {
        when(mockCapabilities.supportsFileBasedSecrets()).thenReturn(true);
        when(mockCapabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-secrets.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationPasses(serviceSpec);
    }

    @Test
    public void testSpecFailsWhenEnvSecretIsNotSupported() throws Exception {
        when(mockCapabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-secrets-env.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationErrorWithValue(serviceSpec, "secrets:env");
    }

    @Test
    public void testSpecSucceedsWhenEnvSecretIsSupported() throws Exception {
        when(mockCapabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-secrets-env.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        checkValidationPasses(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-simple.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(DcosConstants.DEFAULT_GPU_POLICY,
                PodSpecsCannotUseUnsupportedFeatures.serviceRequestsGpuResources(serviceSpec));
    }

    @Test
    public void validGpuResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-gpu-resource.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertTrue("Expected serviceSpec to request support GPUs",
                PodSpecsCannotUseUnsupportedFeatures.serviceRequestsGpuResources(serviceSpec));
    }

    @Test
    public void validGpuResourceSet() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-gpu-resourceset.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertTrue("Expected serviceSpec to request support GPUs",
                PodSpecsCannotUseUnsupportedFeatures.serviceRequestsGpuResources(serviceSpec));
    }

    private static void checkValidationPasses(DefaultServiceSpec serviceSpec) {
        Assert.assertTrue(VALIDATOR.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    private static void checkValidationErrorWithValue(DefaultServiceSpec serviceSpec, String expectedFailedField) {
        Collection<ConfigValidationError> errors = VALIDATOR.validate(Optional.empty(), serviceSpec);
        for (ConfigValidationError err : errors) {
            if (err.getConfigurationValue().equals(expectedFailedField)) {
                return;
            }
        }
        Assert.fail(String.format("Expected error with field %s, got errors: %s", expectedFailedField, errors));
    }
}
