package com.mesosphere.sdk.testing;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.DefaultMustacheFactory;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * Utilities which handle rendering service specifications starting from their Universe packaging, simulating what
 * Cosmos and Marathon would do for a running service.
 */
public class ServiceRenderUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRenderUtils.class);

    /**
     * Universe template values that are injected by SDK tooling. We inject test values here.
     */
    private static final Map<String, Object> RESOURCE_TEMPLATE_PARAMS;
    static {
        RESOURCE_TEMPLATE_PARAMS = new HashMap<>();
        RESOURCE_TEMPLATE_PARAMS.put("jre-url", "https://test-url/jre.tgz");
        RESOURCE_TEMPLATE_PARAMS.put("libmesos-bundle-url", "https://test-url/libmesos-bundle.tgz");
        RESOURCE_TEMPLATE_PARAMS.put("artifact-dir", "https://test-url/artifacts");
    }

    private ServiceRenderUtils() {
        // do not instantiate
    }

    /**
     * Renders the Scheduler's environment for a given service with the provided options. This provides some validation
     * that the service's universe packaging is valid, by exercising config.json, resource.json, and
     * marathon.json.mustache.
     *
     * @param customOptions map of any custom config settings as would be passed via an {@code options.json} file when
     *     installing the service, or an empty map to use defaults defined in the service's {@code config.json}
     * @return Scheduler environment variables resulting from the provided options
     */
    public static Map<String, String> renderSchedulerEnvironment(Map<String, Object> customOptions) {
        Map<String, Object> config = new HashMap<>();
        // Get default values from config.json
        flattenPropertyTree("", new JSONObject(readFile("universe/config.json")), config);

        // Get "resource.*" content from resource.json:
        JSONObject resourceJson = new JSONObject(
                renderMustache("resource.json", readFile("universe/resource.json"), RESOURCE_TEMPLATE_PARAMS));
        flattenTree("resource", resourceJson, config);

        // Override any of the above with the caller's manually-configured settings.
        config.putAll(customOptions);

        // Render marathon.json and get scheduler env content:
        Map<String, String> env = getMarathonAppEnvironment(
                renderMustache("marathon.json.mustache", readFile("universe/marathon.json.mustache"), config));
        LOGGER.info("Rendered Scheduler environment: {}", env);
        return env;
    }

    /**
     * Returns a {@link File} object for a provided filename or relative path within the service's {@code src/main/dist}
     * directory.
     */
    public static File getDistFile(String specFilePath) {
        return new File(System.getProperty("user.dir") + "/src/main/dist/" + specFilePath);
    }

    /**
     * Renders and returns a {@link RawServiceSpec} for a given Service Specification YAML file against the provided
     * {@code schedulerEnvironment}.
     *
     * @param specFile path to the Service Specification YAML file to be rendered
     * @param schedulerEnvironment the simulated scheduler environment, see {@link #renderSchedulerEnvironment(Map)}
     */
    public static RawServiceSpec getRawServiceSpec(File specFile, Map<String, String> schedulerEnvironment)
            throws Exception {
        LOGGER.info("Creating RawServiceSpec from YAML file: {}", specFile.getAbsolutePath());
        return RawServiceSpec.newBuilder(specFile)
                .setEnv(schedulerEnvironment)
                .build();
    }

    /**
     * Renders and returns a {@link ServiceSpec} for a given {@link RawServiceSpec} and Scheduler environment.
     *
     * @param rawServiceSpec the raw ServiceSpec representing the object model of a Service Specification YAML file
     * @param schedulerEnvironment the simulated scheduler environment, see {@link #renderSchedulerEnvironment(Map)}
     */
    public static ServiceSpec getServiceSpec(
            RawServiceSpec rawServiceSpec, Map<String, String> schedulerEnvironment, File configDir) throws Exception {
        return DefaultServiceSpec.newGenerator(
                rawServiceSpec, getMockFlags(), schedulerEnvironment, configDir).build();
    }

    /**
     * Returns a feasible {@link SchedulerFlags} to be used when exercising Scheduler construction.
     */
    static SchedulerFlags getMockFlags() {
        SchedulerFlags mockFlags = Mockito.mock(SchedulerFlags.class);
        Mockito.when(mockFlags.getExecutorURI()).thenReturn("executor-test-uri");
        Mockito.when(mockFlags.getApiServerPort()).thenReturn(8080);
        Mockito.when(mockFlags.getServiceAccountUid()).thenReturn(TestConstants.PRINCIPAL);
        return mockFlags;
    }

    /**
     * Renders the provided mustache template with the provided values, and returns the rendered result.
     */
    private static String renderMustache(String templateName, String templateContent, Map<String, Object> vals) {
        StringWriter writer = new StringWriter();
        new DefaultMustacheFactory()
                .compile(new StringReader(templateContent), templateName)
                .execute(writer, vals);
        return writer.toString();
    }

    /**
     * Finds string entries in the provided tree and writes them to {@code config} under the provided path. Used for
     * Universe resource.json files.
     *
     * For example, {"a": {"b": {"c": "d"}} } => {"a.b.c": "d"}
     */
    private static void flattenTree(String path, JSONObject node, Map<String, Object> config) {
        for (String key : node.keySet()) {
            Object val = node.get(key);
            String entryPath = path.isEmpty() ? key : String.format("%s.%s", path, key);
            if (val instanceof JSONObject) {
                flattenTree(entryPath, (JSONObject) val, config); // RECURSE
            } else if (val instanceof String) {
                config.put(entryPath, val);
            }
        }
    }

    /**
     * Finds entries nested under 'properties' nodes and writes them to {@code config} under the provided path. Used for
     * Universe config.json files.
     *
     * For example, {"a": {"properties": {"b": {"default": "c"}, "d": {"properties": {"e": {"default": "f"}}}}}} =>
     * {"a.b": "c", "a.b.d.e": "f"}
     */
    private static void flattenPropertyTree(String path, JSONObject node, Map<String, Object> config) {
        if (node.has("default")) {
            config.put(path, node.get("default"));
        }
        if (node.has("type") && node.getString("type").equals("object") && node.has("properties")) {
            JSONObject props = node.getJSONObject("properties");
            for (String key : props.keySet()) {
                String entryPath = path.isEmpty() ? key : String.format("%s.%s", path, key);
                flattenPropertyTree(entryPath, props.getJSONObject(key), config); // RECURSE
            }
        }
    }

    /**
     * Given a (fully rendered) marathon.json, returns what the resulting environment would look like.
     */
    private static Map<String, String> getMarathonAppEnvironment(String marathonJsonContent) {
        JSONObject marathonJson = new JSONObject(marathonJsonContent);
        Map<String, String> env = new TreeMap<>();

        if (marathonJson.has("env")) {
            JSONObject marathonEnvJson = marathonJson.getJSONObject("env");
            for (String key : marathonEnvJson.keySet()) {
                env.put(key, marathonEnvJson.getString(key));
            }
        }

        // In addition to the 'env' entries, simulate the injected envvars produced by Marathon for any listed ports:
        if (marathonJson.has("portDefinitions")) {
            JSONArray portsJson = marathonJson.getJSONArray("portDefinitions");
            Random random = new Random();
            for (int i = 0; i < portsJson.length(); ++i) {
                JSONObject portJson = portsJson.getJSONObject(i);
                // Determine a port value. Simulate a random ephemeral port if the value is 0.
                int portVal = portJson.getInt("port");
                if (portVal == 0) {
                    // Default ephemeral port range on linux is 32768 through 60999. Let's simulate that.
                    // See: /proc/sys/net/ipv4/ip_local_port_range
                    portVal = random.nextInt(61000 - 32768 /* result: 0 thru 28231 */) + 32768;
                }
                // Each port is advertised against its index (e.g. $PORTN), and against its name (e.g. $PORT_NAME).
                String portStr = String.valueOf(portVal);
                env.put(String.format("PORT%d", i), portStr);
                if (portJson.has("name")) {
                    env.put(String.format("PORT_%s", portJson.getString("name").toUpperCase()), portStr);
                }
            }
        }
        return env;
    }

    /**
     * Returns the content of the specified file, or throws an {@link IllegalArgumentException} if the file couldn't be
     * accessed.
     */
    private static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format(
                    "Failed to access file at %s (relative to pwd=%s)", path, System.getProperty("user.dir")), e);
        }
    }
}
