package com.mesosphere.sdk.testing;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.curator.test.TestingServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collections;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class encapsulates common features needed for the validation of YAML ServiceSpec files.
 */
public class BaseServiceSpecTest {
    @ClassRule
    public static final EnvironmentVariables ENV_VARS = new EnvironmentVariables();
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
        ENV_VARS.set("CONFIG_TEMPLATE_PATH", new File(yamlFile.getPath()).getParent());
        deserializeServiceSpec(fileName);
        validateServiceSpec(fileName);
    }

    protected void deserializeServiceSpec(String fileName) throws Exception {
        File file;
        try {
            file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        } catch (NullPointerException e) {
            throw new Exception("Did not find file: " + fileName + " perhaps you forgot to link it in the Resources" +
                    "folder?");
        }
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file), mockFlags);
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }

    protected void validateServiceSpec(String fileName) throws Exception {
        File file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec, mockFlags);

        TestingServer testingServer = new TestingServer();
        StateStoreCache.resetInstanceForTests();

        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsGpuResource()).thenReturn(true);
        when(capabilities.supportCniPortMapping()).thenReturn(true);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(capabilities.supportsRLimits()).thenReturn(true);

        DefaultScheduler.newBuilder(serviceSpec, mockFlags)
                .setStateStore(DefaultScheduler.createStateStore(
                        serviceSpec, mockFlags, testingServer.getConnectString()))
                .setConfigStore(DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString()))
                .setCapabilities(capabilities)
                .setPlansFrom(rawServiceSpec)
                .build();
        testingServer.close();
    }
}
