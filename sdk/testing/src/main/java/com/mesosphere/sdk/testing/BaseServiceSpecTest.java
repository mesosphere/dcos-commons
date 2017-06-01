package com.mesosphere.sdk.testing;

import com.google.api.client.util.Joiner;
import com.mesosphere.sdk.config.DefaultTaskEnvRouter;
import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.DefaultServiceSpecBuilder;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecBuilder;
import com.mesosphere.sdk.state.DefaultConfigStore;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;

import org.junit.Assert;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class encapsulates common features needed for the validation of YAML ServiceSpec files.
 */
public class BaseServiceSpecTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Map<String, String> envVars = new TreeMap<>();

    @Mock
    private SchedulerFlags mockFlags;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockFlags.getExecutorURI()).thenReturn("executor-test-uri");
        when(mockFlags.getApiServerPort()).thenReturn(8080);
    }

    protected BaseServiceSpecTest(Map<String, String> envVars) {
        this.envVars.putAll(envVars);
    }

    /**
     * Invoke as: {@code super("key1", "val1", "key2", "val2", ...)}.
     */
    protected BaseServiceSpecTest(String... keyVals) {
        Assert.assertTrue("keyVals.length must be a multiple of two for key=>val mapping", keyVals.length % 2 == 0);
        for (int i = 0; i < keyVals.length; i += 2) {
            this.envVars.put(keyVals[i], keyVals[i + 1]);
        }
    }

    protected void testYaml(String fileName) throws Exception {
        File yamlFile = new File(System.getProperty("user.dir") + "/src/main/dist/" + fileName);
        File file;
        try {
            file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        } catch (NullPointerException e) {
            throw new Exception(
                    "Did not find file: " + fileName + " perhaps you forgot to link it in the Resources folder?");
        }

        Map<String, String> envVars = new TreeMap<>();
        envVars.putAll(this.envVars);
        envVars.put("CONFIG_TEMPLATE_PATH", new File(yamlFile.getPath()).getParent());
        logger.info("Configured environment:\n{}", Joiner.on('\n').join(envVars.entrySet()));

        RawServiceSpec rawServiceSpec = new RawServiceSpecBuilder(file).setEnv(envVars).build();
        DefaultServiceSpec serviceSpec = new DefaultServiceSpecBuilder(
                rawServiceSpec, mockFlags, new DefaultTaskEnvRouter(envVars)).build();
        Assert.assertEquals(8080, serviceSpec.getApiPort());

        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsGpuResource()).thenReturn(true);
        when(capabilities.supportsCNINetworking()).thenReturn(true);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(capabilities.supportsRLimits()).thenReturn(true);

        Persister persister = new MemPersister();
        DefaultScheduler.newBuilder(serviceSpec, mockFlags)
                .setStateStore(new DefaultStateStore(persister))
                .setConfigStore(
                        new DefaultConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister))
                .setCapabilities(capabilities)
                .setPlansFrom(rawServiceSpec)
                .build();
    }
}
