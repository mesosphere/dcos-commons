package com.mesosphere.sdk.specification.yaml;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.StringUtils;

import com.github.mustachejava.Binding;
import com.github.mustachejava.Code;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.ObjectHandler;
import com.github.mustachejava.TemplateContext;
import com.github.mustachejava.codes.ValueCode;
import com.github.mustachejava.reflect.GuardedBinding;
import com.github.mustachejava.reflect.MissingWrapper;
import com.github.mustachejava.reflect.ReflectionObjectHandler;
import com.github.mustachejava.util.Wrapper;
import com.google.common.base.Joiner;

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
     * Available behaviors when a template value is missing.
     */
    public enum MissingBehavior {
        /** Render missing template values as empty strings in the output. */
        EMPTY_STRING,

        /** Throw an exception when missing template values are encountered. */
        EXCEPTION
    }

    /**
     * Renders a given Mustache template using the provided environment map, with the provided behavior when missing
     * template values are encountered.
     *
     * @param templateContent String representation of template
     * @param environment     Map of environment variables
     * @param missingValueBehavior What to do when the {@code templateContent} has a value which isn't present in the
     *      {@code environment}
     * @return Rendered Mustache template String.
     */
    public static String applyEnvToMustache(
            String templateName,
            String templateContent,
            Map<String, String> environment,
            MissingBehavior missingValueBehavior) {
        StringWriter writer = new StringWriter();
        DefaultMustacheFactory mustacheFactory = new DefaultMustacheFactory();
        if (missingValueBehavior == MissingBehavior.EXCEPTION) {
            mustacheFactory.setObjectHandler(new ReflectionObjectHandler() {
                @Override
                public Binding createBinding(String name, final TemplateContext tc, Code code) {
                    return new GuardedBindingThrowsWhenMissing(this, name, tc, code, templateContent, environment);
                }
            });
        }

        Map<String, Object> objEnv = new HashMap<>();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (StringUtils.equalsIgnoreCase(entry.getValue(), "false") ||
                    StringUtils.equalsIgnoreCase(entry.getValue(), "true")) {
                objEnv.put(entry.getKey(), Boolean.valueOf(entry.getValue()));
            } else {
                objEnv.put(entry.getKey(), entry.getValue());
            }
        }

        mustacheFactory
                .compile(new StringReader(templateContent), templateName)
                .execute(writer, objEnv);
        return writer.toString();
    }

    /**
     * An extension of {@link GuardedBinding} which throws when missing template parameters are encountered.
     */
    private static class GuardedBindingThrowsWhenMissing extends GuardedBinding {

        private final TemplateContext tc;
        private final Code code;
        private final String templateContent;
        private final Map<String, String> env;

        private GuardedBindingThrowsWhenMissing(
                ObjectHandler oh,
                String name,
                final TemplateContext tc,
                Code code,
                String templateContent,
                Map<String, String> env) {
            super(oh, name, tc, code);
            this.tc = tc;
            this.code = code;
            this.templateContent = templateContent;
            this.env = env;
        }

        @Override
        protected synchronized Wrapper getWrapper(String name, List<Object> scopes) {
            Wrapper wrapper = super.getWrapper(name, scopes);
            // This should only throw when the template param is e.g. "{{hello}}", not "{{#hello}}hi{{/hello}}". The
            // latter case implies an expectation that the value will sometimes be unset. We can determine the situation
            // based on the code type:
            // - "{{hello}}" = ValueCode <-- check for this case
            // - "{{#hello}}{{/hello}}" = IterableCode
            // - "{{^hello}}{{/hello}}" = NotIterableCode
            // - etc... {{> partial}}, {{! comment}}
            if (code instanceof ValueCode && wrapper instanceof MissingWrapper) {
                Map<String, String> sortedEnv = new TreeMap<>();
                sortedEnv.putAll(env);
                throw new MustacheException(String.format(
                        "Template param %s was not found at template line %s:"
                                + "%n- env:%n %s%n- template:%n%s%n- code: %s",
                        name,
                        tc.line(),
                        Joiner.on("\n ").join(sortedEnv.entrySet()),
                        templateContent,
                        ReflectionToStringBuilder.toString(code)));
            }
            return wrapper;
        }
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
