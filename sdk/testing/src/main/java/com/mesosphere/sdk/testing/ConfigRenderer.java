package com.mesosphere.sdk.testing;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.DefaultMustacheFactory;

public class ConfigRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigRenderer.class);

    /**
     * Values that are injected by SDK tooling. We inject test values here.
     */
    private static final Map<String, Object> RESOURCE_TEMPLATE_PARAMS;
    static {
        RESOURCE_TEMPLATE_PARAMS = new HashMap<>();
        RESOURCE_TEMPLATE_PARAMS.put("jre-url", "https://test-url/jre.tgz");
        RESOURCE_TEMPLATE_PARAMS.put("libmesos-bundle-url", "https://test-url/libmesos-bundle.tgz");
        RESOURCE_TEMPLATE_PARAMS.put("artifact-dir", "https://test-url/artifacts");
    }


    private ConfigRenderer() {
        // do not instantiate
    }

    public static Map<String, String> renderConfig(Map<String, Object> options) {
        LOGGER.info("cwd: {}", System.getProperty("user.dir"));

        Map<String, Object> config = new HashMap<>();
        // Get default values from config.json
        flattenPropertyTree("", new JSONObject(readFile("universe/config.json")), config);

        // Get "resource.*" content from resource.json:
        JSONObject resourceJson = new JSONObject(
                renderMustache("resource.json", readFile("universe/resource.json"), RESOURCE_TEMPLATE_PARAMS));
        flattenTree("resource", resourceJson, config);

        // Override any of the above with the caller's manually-configured settings.
        config.putAll(options);

        // Render marathon.json and get scheduler env content:
        JSONObject marathonEnvJson = new JSONObject(
                renderMustache("marathon.json.mustache", readFile("universe/marathon.json.mustache"), config)).getJSONObject("env");
        Map<String, String> env = new TreeMap<>();
        for (String key : marathonEnvJson.keySet()) {
            env.put(key, marathonEnvJson.getString(key));
        }
        LOGGER.info("env: {}", env);
        return env;
    }

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
            String entryPath = path.isEmpty() ? key: String.format("%s.%s", path, key);
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
     * Returns the content of the specified file, or throws an {@link IllegalArgumentException} if the file couldn't be
     * accessed.
     */
    private static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Failed to access file relative to pwd=%s: %s", System.getProperty("user.dir"), path), e);
        }
    }
}
