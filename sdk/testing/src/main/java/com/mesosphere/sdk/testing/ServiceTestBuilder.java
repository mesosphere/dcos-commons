package com.mesosphere.sdk.testing;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * Exercises the service's packaging and Service Specification YAML file by building a Scheduler object against it.
 *
 * @see ServiceTestUtils for shortcuts in common usage scenarios
 */
public class ServiceTestBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTestBuilder.class);

    private final File specPath;
    private File configTemplateDir;
    private final Map<String, Object> options;
    private final Map<String, String> customSchedulerEnv;

    /**
     * Creates a new instance against the default {@code svc.yml} Service Specification YAML file.
     *
     * <p>WARNING: If you do not invoke the {@link test()} method, your test will not run!
     */
    public ServiceTestBuilder() {
        this("svc.yml");
    }

    /**
     * Creates a new instance against the provided Service Specification YAML path.
     *
     * <p>WARNING: If you do not invoke the {@link #render()} method, your test will not run!
     *
     * @param specPath path to the Service Specification YAML file, relative to the {@code dist} directory
     */
    public ServiceTestBuilder(String specPath) {
        this.specPath = getDistFile(specPath);
        this.configTemplateDir = this.specPath.getParentFile();
        this.options = new HashMap<>();
        this.customSchedulerEnv = new HashMap<>();
    }

    /**
     * Configures the test with a custom options as would be provided via an {@code options.json} file. If this is not
     * invoked then the service defaults from {@code config.json} are used for the test.
     *
     * @param name the option name of the form {@code "section1.section2.section3.name"}
     * @param value the option value, which may be e.g. a String, Integer, or Boolean
     * @return {@code this}
     */
    public ServiceTestBuilder setOption(String name, Object value) {
        this.options.put(name, value);
        return this;
    }

    /**
     * Configures the test with custom options as would be provided via an {@code options.json} file. If this is not
     * invoked then the service defaults from {@code config.json} are used for the test.
     *
     * @param options map of any custom config settings as would be passed via an {@code options.json} file when
     *     installing the service, or an empty map to use defaults defined in the service's {@code config.json}
     * @return {@code this}
     */
    public ServiceTestBuilder setOptions(Map<String, Object> options) {
        this.options.putAll(options);
        return this;
    }

    /**
     * Configures the test with a custom configuration template location. By default config templates are expected to
     * be in the same directory as the Service Specification YAML file.
     *
     * @return {@code this}
     */
    public ServiceTestBuilder setConfigTemplateDir(File configTemplateDir) {
        this.configTemplateDir = configTemplateDir;
        return this;
    }

    /**
     * Configures the test with additional environment variables beyond those which would be included by the service's
     * {@code marathon.json.mustache}. This may be useful for tests against custom Service Specification YAML files
     * which reference envvars that aren't also present in the packaging's Marathon definition. These values will
     * override any produced by the service's packaging.
     *
     * @param customEnvKeyVals an even number of strings which will be unpacked as (key, value, key, value, ...)
     * @return {@code this}
     */
    public ServiceTestBuilder setCustomEnv(String... customEnvKeyVals) {
        if (customEnvKeyVals.length % 2 != 0) {
            throw new IllegalArgumentException(String.format(
                    "Expected an even number of arguments [key, value, key, value, ...], got: %d",
                    customEnvKeyVals.length));
        }
        for (int i = 0; i < customEnvKeyVals.length; i += 2) {
            this.customSchedulerEnv.put(customEnvKeyVals[i], customEnvKeyVals[i + 1]);
        }
        return this;
    }

    /**
     * Exercises the service's packaging and resulting Service Specification YAML file.
     *
     * @return a {@link ServiceTestResult} containing the resulting scheduler environment and ServiceSpec/RawServiceSpec
     * @throws Exception if the test failed
     */
    public ServiceTestResult render() throws Exception {
        SchedulerFlags mockFlags = Mockito.mock(SchedulerFlags.class);
        Mockito.when(mockFlags.getExecutorURI()).thenReturn("executor-test-uri");
        Mockito.when(mockFlags.getApiServerPort()).thenReturn(8080);
        Mockito.when(mockFlags.getServiceAccountUid()).thenReturn(TestConstants.PRINCIPAL);

        Capabilities mockCapabilities = Mockito.mock(Capabilities.class);
        Mockito.when(mockCapabilities.supportsGpuResource()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsCNINetworking()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsNamedVips()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsRLimits()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsPreReservedResources()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsFileBasedSecrets()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsEnvBasedSecretsProtobuf()).thenReturn(true);
        Mockito.when(mockCapabilities.supportsEnvBasedSecretsDirectiveLabel()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);

        Map<String, String> schedulerEnvironment =
                CosmosRenderer.renderSchedulerEnvironment(options, customSchedulerEnv);
        LOGGER.info("Creating RawServiceSpec from YAML file: {}", specPath.getAbsolutePath());
        // Test 1: Does RawServiceSpec render?
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(specPath)
                .setEnv(schedulerEnvironment)
                .build();

        // Test 2: Does ServiceSpec render?
        ServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(
                rawServiceSpec, mockFlags, schedulerEnvironment, configTemplateDir).build();

        // Test 3: Does the scheduler build?
        Persister persister = new MemPersister();
        DefaultScheduler.newBuilder(serviceSpec, mockFlags, persister)
                .setStateStore(new StateStore(persister))
                .setConfigStore(new ConfigStore<>(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister))
                .setPlansFrom(rawServiceSpec)
                .build();

        // Test 4: Do per-task config templates render?
        // TODO(nick): Exercise custom templates, using task env produced by:
        // - TaskEnvRouter (TASKCFG_<foo>_*)
        // - svc.yml's "env" list
        // - what Mesos/Scheduler would automatically provide (FRAMEWORK_HOST, MESOS_SANDBOX, ...)
        // - any custom values provided by the test (for any "export"s preceding config rendering in the cmd)

        // Reset Capabilities API to default behavior:
        Capabilities.overrideCapabilities(null);

        return new ServiceTestResult(serviceSpec, rawServiceSpec, schedulerEnvironment);
    }

    /**
     * Returns a {@link File} object for a provided filename or relative path within the service's {@code src/main/dist}
     * directory.
     */
    public static File getDistFile(String specFilePath) {
        return new File(System.getProperty("user.dir") + "/src/main/dist/" + specFilePath);
    }
}
