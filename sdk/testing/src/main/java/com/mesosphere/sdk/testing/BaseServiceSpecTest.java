package com.mesosphere.sdk.testing;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.curator.test.TestingServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
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

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    protected void testYaml(String fileName) throws Exception {
        File yamlFile = new File(System.getProperty("user.dir") + "/src/main/dist/" + fileName);
        ENV_VARS.set("CONFIG_TEMPLATE_PATH", new File(yamlFile.getPath()).getParent());
        deserializeServiceSpec(fileName);
        validateServiceSpec(fileName);
    }

    protected void deserializeServiceSpec(String fileName) throws Exception {
        File file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }

    protected void validateServiceSpec(String fileName) throws Exception {
        File file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        RawServiceSpec rawServiceSpec = generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = generateServiceSpec(rawServiceSpec);

        TestingServer testingServer = new TestingServer();
        StateStoreCache.resetInstanceForTests();

        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(capabilities.supportsRLimits()).thenReturn(true);

        DefaultScheduler.newBuilder(serviceSpec)
                .setStateStore(DefaultScheduler.createStateStore(serviceSpec, testingServer.getConnectString()))
                .setConfigStore(DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString()))
                .setCapabilities(capabilities)
                .setPlansFrom(rawServiceSpec)
                .build();
        testingServer.close();
    }

}
