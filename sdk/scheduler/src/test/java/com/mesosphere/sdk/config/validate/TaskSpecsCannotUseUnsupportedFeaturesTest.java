package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLToInternalMappers;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * Tests for {@link TaskSpecsCannotUseUnsupportedFeatures}.
 */
public class TaskSpecsCannotUseUnsupportedFeaturesTest {
    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();
    private static final PodSpecsCannotUseUnsupportedFeatures VALIDATOR = new PodSpecsCannotUseUnsupportedFeatures();

    @Mock
    private Capabilities mockCapabilities;
    @Mock private YAMLToInternalMappers.ConfigTemplateReader mockConfigTemplateReader;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTaskSpecFailsWithShm() throws Exception {
        when(mockCapabilities.supportsShm()).thenReturn(false);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-shm-spec.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();
        checkValidationErrorWithValue(serviceSpec, "shm");
    }


    @Test
    public void testTaskSpecSucceedsWithShm() throws Exception {
        when(mockCapabilities.supportsShm()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        File file = new File(getClass().getClassLoader().getResource("valid-shm-spec.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG)
                .build();
        checkValidationPasses(serviceSpec);
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
