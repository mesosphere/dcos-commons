package com.mesosphere.sdk.specification.yaml;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.testutils.TestConstants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

public class YAMLServiceSpecFactoryTest {
    private static final Map<String, String> YAML_ENV_MAP = new HashMap<>();
    static {
        YAML_ENV_MAP.put("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
    }

    @Mock private SchedulerConfig mockSchedulerConfig;
    @Mock private YAMLToInternalMappers.ConfigTemplateReader mockConfigTemplateReader;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGenerateSpecFromYAML() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockConfigTemplateReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockConfigTemplateReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockConfigTemplateReader.read("config-three.conf.mustache")).thenReturn("hi");
        when(mockSchedulerConfig.getApiServerPort()).thenReturn(123);
        when(mockSchedulerConfig.getExecutorURI()).thenReturn("test-executor-uri");

        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, mockSchedulerConfig)
                .setConfigTemplateReader(mockConfigTemplateReader)
                .build();
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void testGenerateRawSpecFromYAMLFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(file).setEnv(YAML_ENV_MAP).build();
        Assert.assertNotNull(rawServiceSpec);
    }
}
