package com.mesosphere.sdk.testing;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.mockito.Mockito;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;

/**
 * Exercises the service's packaging and Service Specification YAML file by building a Scheduler object against it.
 *
 * @see ServiceSpecTestUtils for shortcuts in common usage scenarios
 */
public class ServiceSpecTestBuilder {

    private final File specPath;
    private File configTemplateDir;
    private final Map<String, Object> options;
    private final Map<String, String> customSchedulerEnv;

    /**
     * Creates a new instance against the default "svc.yml" Service Specification YAML filename.
     * WARNING: If you do not invoke the {@link test()} method, your test will not run!
     */
    public ServiceSpecTestBuilder() {
        this(ServiceRenderUtils.getDistFile("svc.yml"));
    }

    /**
     * Creates a new instance against the provided Service Specification YAML filename.
     * WARNING: If you do not invoke the {@link test()} method, your test will not run!
     *
     * @param specPath path to the Service Specification YAML file
     */
    public ServiceSpecTestBuilder(File specPath) {
        this.specPath = specPath;
        this.configTemplateDir = specPath.getParentFile();
        this.options = new TreeMap<>();
        this.customSchedulerEnv = new HashMap<>();
    }

    /**
     * Configures the test with custom options as would be provided via an {@code options.json} file. If this is not
     * invoked then the service defaults from {@code config.json} are used for the test.
     *
     * @param options map of any custom config settings as would be passed via an {@code options.json} file when
     *     installing the service, or an empty map to use defaults defined in the service's {@code config.json}
     * @return {@code this}
     */
    public ServiceSpecTestBuilder setOptions(Map<String, Object> options) {
        this.options.clear();
        this.options.putAll(options);
        return this;
    }

    /**
     * Configures the test with a custom configuration template location. By default config templates are expected to
     * be in the same directory as the Service Specification YAML file.
     *
     * @return {@code this}
     */
    public ServiceSpecTestBuilder setConfigTemplateDir(File configTemplateDir) {
        this.configTemplateDir = configTemplateDir;
        return this;
    }

    /**
     * Version of {@link #setCustomEnv(Map)} which doesn't require constructing a map.
     *
     * @param customEnvKeyVals an even number of strings which will be unpacked as (key, value, key, value, ...)
     */
    public ServiceSpecTestBuilder setCustomEnv(String... customEnvKeyVals) {
        if (customEnvKeyVals.length % 2 != 0) {
            throw new IllegalArgumentException(String.format(
                    "Expected an even number of arguments (key, value, key, value, ...), got: %d",
                    customEnvKeyVals.length));
        }
        Map<String, String> customEnv = new HashMap<>();
        for (int i = 0; i < customEnvKeyVals.length; i += 2) {
            customEnv.put(customEnvKeyVals[i], customEnvKeyVals[i + 1]);
        }
        return setCustomEnv(customEnv);
    }

    /**
     * Configures the test with additional environment variables beyond those which would be included by the service's
     * {@code marathon.json.mustache}. This may be useful for tests against custom Service Specification YAML files
     * which reference envvars that aren't also present in the packaging's Marathon definition. These values will
     * override any produced by the service's packaging.
     *
     * @param customEnv map of environment variables to be included in addition to what's in {@code marathon.json}
     * @return {@code this}
     */
    public ServiceSpecTestBuilder setCustomEnv(Map<String, String> customEnv) {
        this.customSchedulerEnv.clear();
        this.customSchedulerEnv.putAll(customEnv);
        return this;
    }

    /**
     * Exercises the service's packaging and resulting Service Specification YAML file.
     *
     * @throws Exception if the test failed
     */
    public void test() throws Exception {
        Map<String, String> schedulerEnvironment = new TreeMap<>();
        schedulerEnvironment.putAll(ServiceRenderUtils.renderSchedulerEnvironment(options));
        schedulerEnvironment.putAll(customSchedulerEnv);
        RawServiceSpec rawServiceSpec = ServiceRenderUtils.getRawServiceSpec(specPath, schedulerEnvironment);
        ServiceSpec serviceSpec =
                ServiceRenderUtils.getServiceSpec(rawServiceSpec, schedulerEnvironment, configTemplateDir);

        Capabilities mockCapabilities = Mockito.mock(Capabilities.class);
        Mockito.when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsCNINetworking()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsNamedVips()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsRLimits()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsPreReservedResources()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsFileBasedSecrets()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsEnvBasedSecretsDirectiveLabel()).thenReturn(true);

        Persister persister = new MemPersister();
        Capabilities.overrideCapabilities(mockCapabilities);
        DefaultScheduler.newBuilder(serviceSpec, ServiceRenderUtils.getMockFlags(), persister)
                .setStateStore(new StateStore(persister))
                .setConfigStore(new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister))
                .setPlansFrom(rawServiceSpec)
                .build();
    }
}
