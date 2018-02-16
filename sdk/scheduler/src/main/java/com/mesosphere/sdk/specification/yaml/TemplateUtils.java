package com.mesosphere.sdk.specification.yaml;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

/**
 * Utility methods relating to rendering mustache templates.
 */
public class TemplateUtils {

    private TemplateUtils() {
        // do not instantiate
    }

    /**
     * Some information about a Mustache template parameter which wasn't found in the provided environment map. The
     * caller may use this information to determine if the template was successfully rendered.
     */
    public static class MissingValue {
        /**
         * The name of the missing value.
         */
        public final String name;

        /**
         * The line number where the missing value was encountered.
         */
        public final int line;

        private MissingValue(String name, int line) {
            this.name = name;
            this.line = line;
        }

        @Override
        public String toString() {
            return String.format("%s@L%d", name, line);
        }
    }

    /**
     * Renders a given Mustache template using the provided value map, returning any template parameters which weren't
     * present in the map.
     *
     * @param templateContent String representation of template
     * @param values Map of values to be inserted into the template
     * @param missingValues List where missing value entries will be added for any template params in
     *     {@code templateContent} which are not found in {@code values}
     * @return Rendered Mustache template String
     */
    public static String renderMustache(
            String templateName,
            String templateContent,
            Map<String, String> values,
            final List<MissingValue> missingValues) {
        StringWriter writer = new StringWriter();
        DefaultMustacheFactory mustacheFactory = new DefaultMustacheFactory();
        mustacheFactory.setObjectHandler(new ReflectionObjectHandler() {
            @Override
            public Binding createBinding(String name, final TemplateContext tc, Code code) {
                return new MissingValueBinding(this, name, tc, code, missingValues);
            }
        });

        Map<String, Object> objEnv = new HashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
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
     * Renders a given Mustache template using the provided value map, throwing an exception if any template parameters
     * weren't found in the map.
     *
     * @param templateContent String representation of template
     * @param values Map of values to be inserted into the template
     * @return Rendered Mustache template String
     * @throws MustacheException if parameters in the {@code templateContent} weren't provided in the {@code values}
     */
    public static String renderMustacheThrowIfMissing(
            String templateName, String templateContent, Map<String, String> values) throws MustacheException {
        List<MissingValue> missingValues = new ArrayList<>();
        String rendered = renderMustache(templateName, templateContent, values, missingValues);
        validateMissingValues(templateName, values, missingValues);
        return rendered;
    }

    /**
     * Throws a descriptive exception if {@code missingValues} is non-empty. Exposed as a utility function to allow
     * custom filtering of missing values before the validation occurs.
     */
    public static void validateMissingValues(
            String templateName, Map<String, String> values, Collection<MissingValue> missingValues)
                    throws MustacheException {
        if (!missingValues.isEmpty()) {
            Map<String, String> orderedValues = new TreeMap<>();
            orderedValues.putAll(values);
            throw new MustacheException(String.format(
                    "Missing %d value%s when rendering %s:%n- Missing values: %s%n- Provided values: %s",
                    missingValues.size(),
                    missingValues.size() == 1 ? "" : "s",
                    templateName,
                    missingValues,
                    orderedValues));
        }
    }

    /**
     * An extension of {@link GuardedBinding} which collects missing values against the provided list.
     */
    private static class MissingValueBinding extends GuardedBinding {

        private final TemplateContext tc;
        private final Code code;
        private final List<MissingValue> missingValues;

        private MissingValueBinding(
                ObjectHandler oh,
                String name,
                final TemplateContext tc,
                Code code,
                List<MissingValue> missingValues) {
            super(oh, name, tc, code);
            this.tc = tc;
            this.code = code;
            this.missingValues = missingValues;
        }

        @Override
        protected synchronized Wrapper getWrapper(String name, List<Object> scopes) {
            Wrapper wrapper = super.getWrapper(name, scopes);
            // This should only do anything when the template param is e.g. "{{hello}}", not "{{#hello}}hi{{/hello}}".
            // The latter case implies an expectation that the value will sometimes be unset. We can determine the
            // situation based on the code type:
            // - "{{hello}}" = ValueCode <-- check for this case
            // - "{{#hello}}{{/hello}}" = IterableCode
            // - "{{^hello}}{{/hello}}" = NotIterableCode
            // - etc... "{{>partial}}", "{{!comment}}"
            if (code instanceof ValueCode && wrapper instanceof MissingWrapper) {
                missingValues.add(new MissingValue(name, tc.line()));
            }
            return wrapper;
        }
    }
}
