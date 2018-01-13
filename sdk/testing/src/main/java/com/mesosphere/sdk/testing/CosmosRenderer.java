package com.mesosphere.sdk.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mesosphere.sdk.specification.yaml.TemplateUtils;

/**
 * Reads a service's Universe packaging configuration, and uses that to generate the resulting Scheduler environment.
 * This emulates what Cosmos and Marathon would do for a service as it's installed in a real cluster.
 */
public class CosmosRenderer {

    private static final Random RANDOM = new Random();

    /**
     * resource.json template values that are injected by SDK tooling. We inject test values here.
     * See also: tools/universe/package_builder.py
     */
    private static final Map<String, String> RESOURCE_TEMPLATE_PARAMS;
    static {
        RESOURCE_TEMPLATE_PARAMS = new HashMap<>();
        RESOURCE_TEMPLATE_PARAMS.put("artifact-dir", "https://test-url/artifacts");
        RESOURCE_TEMPLATE_PARAMS.put("jre-url", "https://test-url/jre.tgz");
        RESOURCE_TEMPLATE_PARAMS.put("jre-jce-unlimited-url", "https://test-url/jre-jce-unlimited.tgz");
        RESOURCE_TEMPLATE_PARAMS.put("libmesos-bundle-url", "https://test-url/libmesos-bundle.tgz");
    }

    /**
     * marathon.json.mustache template values that are injected by SDK tooling. We inject test values here.
     * See also: tools/universe/package_builder.py
     */
    private static final Map<String, String> MARATHON_TEMPLATE_PARAMS;
    static {
        MARATHON_TEMPLATE_PARAMS = new HashMap<>();
        MARATHON_TEMPLATE_PARAMS.put("package-name", "test-pkg");
        MARATHON_TEMPLATE_PARAMS.put("package-version", "0.0.1-beta");
        MARATHON_TEMPLATE_PARAMS.put("package-build-time-epoch-ms", "0");
        MARATHON_TEMPLATE_PARAMS.put("package-build-time-str", "Today");
    }

    private CosmosRenderer() {
        // do not instantiate
    }

    /**
     * Renders the Scheduler's environment for a given service with the provided options. This provides some validation
     * that the service's universe packaging is valid, by exercising config.json, resource.json, and
     * marathon.json.mustache.
     *
     * @param customPackageOptions map of any custom config settings as would be passed via an {@code options.json} file
     *     when installing the service. these are provided to {@code config.json}
     * @param customBuildTemplateParams map of any custom template params that are normally provided as
     *     {@code TEMPLATE_X} envvars when building the service at the commandline. these are provided to all universe
     *     files (config.json, marathon.json.mustache, and resource.json)
     * @return Scheduler environment variables resulting from the provided options
     */
    public static Map<String, String> renderSchedulerEnvironment(
            Map<String, String> customPackageOptions, Map<String, String> customBuildTemplateParams) {
        Map<String, String> marathonParams = new HashMap<>();

        // Get default values from config.json (after doing any needed simulated build rendering):
        // IF THIS FAILS IN YOUR TEST: Missing something in customBuildTemplateParams? Bad config.json syntax?
        JSONObject configJson = new JSONObject(TemplateUtils.renderMustacheThrowIfMissing(
                "universe/config.json", readFile("universe/config.json"), customBuildTemplateParams));
        flattenPropertyTree("", configJson, marathonParams);

        // Get "resource.*" content from resource.json (after doing any needed simulated build rendering):
        Map<String, String> resourceParams = new HashMap<>();
        resourceParams.putAll(customBuildTemplateParams);
        resourceParams.putAll(RESOURCE_TEMPLATE_PARAMS);
        List<TemplateUtils.MissingValue> missingResourceParams = new ArrayList<>();
        JSONObject resourceJson = new JSONObject(TemplateUtils.renderMustache(
                "universe/resource.json", readFile("universe/resource.json"), resourceParams, missingResourceParams));
        // For resource.json, use custom template validation to be permissive of missing "sha256:..." params.
        // Developers may customize sha256 params to specify a manifest URL when using the default CLI.
        missingResourceParams = missingResourceParams.stream()
                .filter(val -> !val.name.startsWith("sha256:"))
                .collect(Collectors.toList());
        // IF THIS FAILS IN YOUR TEST: Missing something in customBuildTemplateParams? Bad resource.json syntax?
        TemplateUtils.validateMissingValues("universe/resource.json", resourceParams, missingResourceParams);
        flattenTree("resource", resourceJson, marathonParams);

        // Override any of the above with the caller's manually-configured settings, and with the tooling settings.
        marathonParams.putAll(customPackageOptions);
        marathonParams.putAll(customBuildTemplateParams);
        marathonParams.putAll(MARATHON_TEMPLATE_PARAMS);

        // Render marathon.json and get scheduler env content:
        Map<String, String> env = new TreeMap<>(); // for ordered output in logs
        // IF THIS FAILS: Missing entry in customPackageOptions or customBuildTemplateParams? Bad marathon.json syntax?
        String marathonJson = TemplateUtils.renderMustacheThrowIfMissing(
                "universe/marathon.json.mustache", readFile("universe/marathon.json.mustache"), marathonParams);
        env.putAll(getMarathonAppEnvironment(marathonJson));
        return env;
    }

    /**
     * Finds string entries in the provided tree and writes them to {@code config} under the provided path. Used for
     * Universe resource.json files.
     *
     * For example, {"a": {"b": {"c": "d"}} } => {"a.b.c": "d"}
     */
    private static void flattenTree(String path, JSONObject node, Map<String, String> config) {
        for (String key : node.keySet()) {
            Object val = node.get(key);
            String entryPath = path.isEmpty() ? key : String.format("%s.%s", path, key);
            if (val instanceof JSONObject) {
                flattenTree(entryPath, (JSONObject) val, config); // RECURSE
            } else {
                config.put(entryPath, val.toString());
            }
        }
    }

    /**
     * Finds entries nested under 'properties' nodes in the provided JSON {@code node} and writes them to {@code config}
     * under the provided path. Used for Universe config.json files.
     *
     * For example, {"a": {"properties": {"b": {"default": "c"}, "d": {"properties": {"e": {"default": "f"}}}}}} =>
     * {"a.b": "c", "a.b.d.e": "f"}
     */
    private static void flattenPropertyTree(String path, JSONObject node, Map<String, String> config) {
        if (node.has("default")) {
            // So... this is a fun little hack that fixes the fact that we remove too many escapes
            // when parsing escaped strings.
            config.put(path, node.get("default").toString().replaceAll("\"", "\\\\\""));
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
        Map<String, String> env = new HashMap<>();

        if (marathonJson.has("env")) {
            JSONObject marathonEnvJson = marathonJson.getJSONObject("env");
            for (String key : marathonEnvJson.keySet()) {
                env.put(key, marathonEnvJson.getString(key));
            }
        }

        // In addition to the 'env' entries, simulate the injected envvars produced by Marathon for any listed ports:
        if (marathonJson.has("portDefinitions")) {
            JSONArray portsJson = marathonJson.getJSONArray("portDefinitions");
            for (int i = 0; i < portsJson.length(); ++i) {
                JSONObject portJson = portsJson.getJSONObject(i);
                // Determine a port value. Simulate a random ephemeral port if the value is 0.
                int portVal = portJson.getInt("port");
                if (portVal == 0) {
                    // Default ephemeral port range on linux is 32768 through 60999. Let's simulate that.
                    // See: /proc/sys/net/ipv4/ip_local_port_range
                    portVal = RANDOM.nextInt(61000 - 32768 /* result: 0 thru 28231 */) + 32768;
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
