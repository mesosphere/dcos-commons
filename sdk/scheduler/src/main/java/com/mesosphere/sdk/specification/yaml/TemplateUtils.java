package com.mesosphere.sdk.specification.yaml;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

/**
 * Utility methods relating to rendering mustache templates.
 */
public class TemplateUtils {

    /**
     * Pattern for envvar names surrounded by "{{" "}}".
     * {@link Pattern.DOTALL} is needed to ensure that we scan beyond the first line...
     */
    private static final Pattern MUSTACHE_PATTERN = Pattern.compile(".*\\{\\{[a-zA-Z0-9_]+\\}\\}.*", Pattern.DOTALL);

    private TemplateUtils() {
        // do not instantiate
    }

    /**
     * Renders a given Mustache template using the provided environment map.
     *
     * @param templateContent String representation of template.
     * @param environment     Map of environment variables.
     * @return Rendered Mustache template String.
     */
    public static String applyEnvToMustache(String templateContent, Map<String, Object> environment) {
        StringWriter writer = new StringWriter();
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(new StringReader(templateContent), "configTemplate");
        mustache.execute(writer, environment);
        return writer.toString();
    }

    /**
     * Returns whether the provided content still contains any "{{ }}" mustache templating.
     *
     * @param templateContent the content to be evaluated
     */
    public static boolean isMustacheFullyRendered(String templateContent) {
        return StringUtils.isEmpty(templateContent) || !MUSTACHE_PATTERN.matcher(templateContent).matches();
    }
}
