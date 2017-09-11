package com.mesosphere.sdk.specification.yaml;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import com.github.mustachejava.*;
import org.apache.commons.lang3.StringUtils;

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

//        mustacheFactory
//                .compile(new StringReader(templateContent), templateName)
//                .execute(writer, objEnv);
        return executeMustache(mustacheFactory, writer, templateContent, templateName, objEnv).toString();
    }

    public static final class DiskConfig {
        public String Type;
        public String Root;
        public String Path;
        public int Size;
        public boolean Last;

        public DiskConfig(String tp, String root, String path, int size, boolean last) {
            this.Type = tp;
            this.Root = root;
            this.Path = path;
            this.Size = size;
            this.Last = last;
        }
    }

    public static List<DiskConfig> parseDisksString(String disks) {
        List<DiskConfig> output = new ArrayList<>();
        String[] tmp = disks.split(";");
        for (String disk : tmp) {
            String[] spec = disk.split(",");
            String type = spec[0];
            String root = spec[1];
            String path = spec[2];
            int size = Integer.parseInt(spec[3]);
            output.add(new DiskConfig(type, root, path, size, false));
        }
        output.get(output.size() - 1).Last = true;
        return output;
    }

    /**
     * Executes a Mustache template, using some extra functions to parse things like disk specs.
     *
     * @param templateContent String representation of template.
     * @param environment     Map of environment variables.
     * @return Rendered Mustache template String.
     */
    public static Writer executeMustache(MustacheFactory factory,
                                         Writer writer, String templateContent,
                                         String templateName,
                                         Map<String, Object> environment) {
        Map<String, Object> objectEnvironment = new HashMap<>();
        objectEnvironment.putAll(environment);

        Function<String, String> parseDisks = input -> {
            try {
                String inputVarName = (String)environment.getOrDefault("PARSE_DISKS_INPUT_VAR",
                        "DATA_DISKS");
                String outputVarName = (String)environment.getOrDefault("PARSE_DISKS_OUTPUT_VAR",
                        "data_disks");
                String inputVar = (String)environment.get(inputVarName);
                List<DiskConfig> outputDisks = parseDisksString(inputVar);
                objectEnvironment.put(outputVarName, outputDisks);
            } catch (Exception e) {
                return "";
                //date jos
            }
            return "";
        };
        objectEnvironment.put("ParseDisks", parseDisks);
        Mustache mustache = factory.compile(new StringReader(templateContent), templateName);
        return mustache.execute(writer, objectEnvironment);
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
        return rendered;
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
