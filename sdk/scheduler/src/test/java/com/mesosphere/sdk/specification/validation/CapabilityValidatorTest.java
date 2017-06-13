package com.mesosphere.sdk.specification.validation;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLToInternalMappers;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

import static org.mockito.Mockito.when;

/**
 * This class tests {@link CapabilityValidator}.
 */
public class CapabilityValidatorTest {
    private static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

    @Mock private Capabilities mockCapabilities;
    @Mock private YAMLToInternalMappers.FileReader mockFileReader;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSpecSucceedsWithoutRLimits() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(false);
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);
        CapabilityValidator capabilityValidator = new CapabilityValidator();

        File file = new File(getClass().getClassLoader().getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .build();

        capabilityValidator.validate(serviceSpec);
    }

    @Test
    public void testSpecSucceedsWithRLimits() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(true);
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        when(mockCapabilities.supportsCNINetworking()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);
        CapabilityValidator capabilityValidator = new CapabilityValidator();

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .setFileReader(mockFileReader)
                .build();

        capabilityValidator.validate(serviceSpec);
    }

    @Test(expected = CapabilityValidator.CapabilityValidationException.class)
    public void testSpecFailsWithRLimitsButWithoutCapability() throws Exception {
        when(mockCapabilities.supportsRLimits()).thenReturn(false);
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);
        CapabilityValidator capabilityValidator = new CapabilityValidator();

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .setFileReader(mockFileReader)
                .build();

        capabilityValidator.validate(serviceSpec);
    }

    // TODO (arand) needs to be updated for GPU resource set
    @Test(expected = CapabilityValidator.CapabilityValidationException.class)
    public void testSpecFailsWhenGpuResourceNotSupported() throws Exception {
        when(mockCapabilities.supportsGpuResource()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);
        CapabilityValidator capabilityValidator = new CapabilityValidator();

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        // check that it works when GPUs are specified at the task level
        File file = new File(getClass().getClassLoader().getResource("valid-gpu-resource.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .setFileReader(mockFileReader)
                .build();
        capabilityValidator.validate(serviceSpec);

        // check that it works when GPUs are specified at the resourceSet level
        file = new File(getClass().getClassLoader().getResource("valid-gpu-resourceset.yml").getFile());
        serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags).setFileReader(mockFileReader).build();
        capabilityValidator.validate(serviceSpec);
    }

    @Test
    public void testSpecSucceedsWhenGpuResourceIsSupported() throws Exception {
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);
        CapabilityValidator capabilityValidator = new CapabilityValidator();

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(getClass().getClassLoader().getResource("valid-gpu-resource.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .setFileReader(mockFileReader)
                .build();

        capabilityValidator.validate(serviceSpec);

        when(mockCapabilities.supportsRLimits()).thenReturn(true);
        when(mockCapabilities.supportsCNINetworking()).thenReturn(true);
        File file2 = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file2).build(), flags).setFileReader(mockFileReader).build();

        capabilityValidator.validate(serviceSpec);
    }

    @Test(expected = CapabilityValidator.CapabilityValidationException.class)
    public void testSpecFailsWhenCniPortMappingIsNotSupported() throws Exception {
        when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        when(mockCapabilities.supportsRLimits()).thenReturn(true);

        Capabilities.overrideCapabilities(mockCapabilities);
        CapabilityValidator capabilityValidator = new CapabilityValidator();

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file2 = new File(getClass().getClassLoader().getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file2).build(), flags)
                .setFileReader(mockFileReader)
                .build();
        capabilityValidator.validate(serviceSpec);
    }
}
