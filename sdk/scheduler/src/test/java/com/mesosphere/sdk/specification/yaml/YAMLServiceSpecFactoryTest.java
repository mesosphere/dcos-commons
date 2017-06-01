package com.mesosphere.sdk.specification.yaml;

import org.apache.commons.io.FileUtils;

import com.mesosphere.sdk.scheduler.SchedulerFlags;
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

import static com.mesosphere.sdk.specification.yaml.DefaultServiceSpecBuilder.*;
import static org.mockito.Mockito.when;

public class YAMLServiceSpecFactoryTest {
    private static final Map<String, String> YAML_ENV_MAP = new HashMap<>();
    static {
        YAML_ENV_MAP.put("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
    }

    @Mock private SchedulerFlags mockFlags;
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
        when(mockFlags.getApiServerPort()).thenReturn(123);
        when(mockFlags.getExecutorURI()).thenReturn("test-executor-uri");

        DefaultServiceSpec serviceSpec = new DefaultServiceSpecBuilder(new RawServiceSpecBuilder(file).build(), mockFlags)
                .setFileReader(mockFileReader)
                .build();
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(Integer.valueOf(123), Integer.valueOf(serviceSpec.getApiPort()));
    }

    @Test
    public void testGenerateRawSpecFromYAMLFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        RawServiceSpec rawServiceSpec = new RawServiceSpecBuilder(file).setEnv(YAML_ENV_MAP).build();
        Assert.assertNotNull(rawServiceSpec);
        Assert.assertEquals(TestConstants.PORT_API_VALUE, rawServiceSpec.getScheduler().getApiPort());
    }

    @Test
    public void testGenerateRawSpecFromYAMLString() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        String yaml = FileUtils.readFileToString(file);
        RawServiceSpec rawServiceSpec = new RawServiceSpecBuilder(yaml).setEnv(YAML_ENV_MAP).build();
        Assert.assertNotNull(rawServiceSpec);
        Assert.assertEquals(TestConstants.PORT_API_VALUE, rawServiceSpec.getScheduler().getApiPort());
    }
}
