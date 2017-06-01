package com.mesosphere.sdk.testing;

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

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class encapsulates common features needed for the validation of YAML ServiceSpec files.
 */
public class BaseServiceSpecTest {
    public static final Map<String, String> ENV_VARS = new TreeMap<>();
    @Mock
    private SchedulerFlags mockFlags;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockFlags.getExecutorURI()).thenReturn("executor-test-uri");
        when(mockFlags.getApiServerPort()).thenReturn(8080);
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
        envVars.putAll(ENV_VARS);
        envVars.put("CONFIG_TEMPLATE_PATH", new File(yamlFile.getPath()).getParent());
        RawServiceSpec rawServiceSpec = new RawServiceSpecBuilder(file).setEnv(envVars).build();
        DefaultServiceSpec serviceSpec =
                new DefaultServiceSpecBuilder(rawServiceSpec, mockFlags, new DefaultTaskEnvRouter(ENV_VARS)).build();
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
