package com.mesosphere.sdk.offer.evaluate.placement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import jersey.repackaged.com.google.common.collect.Lists;

/**
 * Implements support for generating {@link PlacementRule}s from Marathon-style constraint strings.
 *
 * @see https://mesosphere.github.io/marathon/docs/constraints.html
 */
public class MarathonConstraintParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarathonConstraintParser.class);
    private static final char ESCAPE_CHAR = '\\';

    private static final String HOSTNAME_FIELD = "hostname";
    private static final Map<String, Operator> SUPPORTED_OPERATORS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        SUPPORTED_OPERATORS.put("UNIQUE", new UniqueOperator());
        SUPPORTED_OPERATORS.put("CLUSTER", new ClusterOperator());
        SUPPORTED_OPERATORS.put("GROUP_BY", new GroupByOperator());
        SUPPORTED_OPERATORS.put("LIKE", new LikeOperator());
        SUPPORTED_OPERATORS.put("UNLIKE", new UnlikeOperator());
        SUPPORTED_OPERATORS.put("MAX_PER", new MaxPerOperator());
    }

    private MarathonConstraintParser() {
        // do not instantiate
    }

    /**
     * ANDs the provided marathon-style constraint string onto the provided hard-coded
     * {@link PlacementRule}, or returns the provided {@link PlacementRule} as-is if the
     * marathon-style constraint is {@code null} or empty.
     *
     * @throws IOException if {@code marathonConstraints} couldn't be parsed, or if the parsed
     *     content isn't valid or supported
     */
    public static PlacementRule parseWith(PlacementRule rule, String marathonConstraints)
            throws IOException {
        PlacementRule marathonRule = parse(marathonConstraints);
        if (marathonRule instanceof PassthroughRule) {
            return rule; // pass-through original rule
        }
        return new AndRule(rule, marathonRule);
    }

    /**
     * Creates and returns a new {@link PlacementRule} against the provided marathon-style
     * constraint string. Returns a {@link PassthroughRule} if the provided constraint string is
     * {@code null} or empty.
     *
     * @param marathonConstraints the marathon-style constraint string, containing one or more
     *     nested json list entries of the form {@code [["multi","list","value"],["hello","hi"]]},
     *     or one or more colon-separated entries of the form {@code multi:list:value,hello:hi},
     *     or a {@code null} or empty value if no constraint is defined
     * @throws IOException if {@code marathonConstraints} couldn't be parsed, or if the parsed
     *     content isn't valid or supported
     */
    public static PlacementRule parse(String marathonConstraints) throws IOException {
        if (marathonConstraints == null || marathonConstraints.isEmpty()) {
            // nothing to enforce
            return new PassthroughRule();
        }
        List<List<String>> rows = splitConstraints(marathonConstraints);
        if (rows.size() == 1) {
            // skip AndRule:
            return parseRow(rows.get(0));
        }
        List<PlacementRule> rowRules = new ArrayList<>();
        for (List<String> row : rows) {
            rowRules.add(parseRow(row));
        }
        return new AndRule(rowRules);
    }

    /**
     * Converts the provided marathon constraint entry to a PlacementRule.
     *
     * @param row a list with size 2 or 3
     * @throws IOException if the provided constraint entry is invalid
     */
    private static PlacementRule parseRow(List<String> row) throws IOException {
        if (row.size() < 2 || row.size() > 3) {
            throw new IOException(String.format(
                    "Invalid number of entries in rule. Expected 2 or 3, got %s: %s",
                    row.size(), row));
        }
        // row fields: fieldname, operatorname[, parameter]
        final String fieldName = row.get(0);
        final String operatorName = row.get(1);
        final Optional<String> parameter = Optional.ofNullable(row.size() >= 3 ? row.get(2) : null);
        Operator operator = SUPPORTED_OPERATORS.get(operatorName);
        if (operator == null) {
            throw new IOException(String.format(
                    "Unsupported operator: '%s' in constraint: %s " +
                    "(expected one of: UNIQUE, CLUSTER, GROUP_BY, LIKE, UNLIKE, or MAX_PER)",
                    operatorName, row));
        }
        PlacementRule rule = operator.run(fieldName, operatorName, parameter);
        LOGGER.info("Marathon-style row '{}' resulted in placement rule: '{}'", row, rule);
        return rule;
    }

    /**
     * Splits the provided marathon constraint statement into elements. Doesn't do any validation
     * on the element contents.
     */
    @VisibleForTesting
    static List<List<String>> splitConstraints(String marathonConstraints) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // The marathon doc uses a format like: '[["a", "b", "c"], ["d", "e"]]'
        // Meanwhile the marathon web interface uses a format like: 'a:b:c,d:e'
        try {
            // First try: ["a", "b", "c"]
            // This format technically isn't present in the Marathon docs, but we're being lenient here.
            List<String> row = mapper.readValue(marathonConstraints, new TypeReference<List<String>>(){});
            LOGGER.debug("Flat list '{}' => single row: '{}'", marathonConstraints, row);
            return Arrays.asList(row);
        } catch (IOException | ClassCastException e1) {
            try {
                // Then try: [["a", "b", "c"], ["d", "e"]]
                List<List<String>> rows =
                        mapper.readValue(marathonConstraints, new TypeReference<List<List<String>>>(){});
                LOGGER.debug("Nested list '{}' => {} rows: '{}'", marathonConstraints, rows.size(), rows);
                return rows;
            } catch (IOException | ClassCastException e2) { // May throw ClassCastException as well as IOException
                // Finally try: a:b:c,d:e
                // Note: We use Guava's Splitter rather than String.split(regex) in order to correctly
                // handle empty trailing fields like 'a:b:' => ['a', 'b', ''] (shouldn't come up but just in case).
                List<List<String>> rows = new ArrayList<>();
                // Allow backslash-escaping of commas or colons within regexes:
                for (String rowStr : escapedSplit(marathonConstraints, ',')) {
                    rows.add(Lists.newArrayList(escapedSplit(rowStr, ':')));
                }
                LOGGER.debug("Comma/colon-separated '{}' => {} rows: '{}'", marathonConstraints, rows.size(), rows);
                return rows;
            }
        }
    }

    /**
     * Tokenizes the provided string using the provided split character. Honors any backslash
     * escaping within the provided string. Tokens in the returned list are automatically trimmed.
     *
     * This is equivalent to calling {@code Splitter.on(split).trimResults().split(str)}, except
     * with the addition of support for escaping the 'split' value with backslashes.
     */
    @VisibleForTesting
    static Collection<String> escapedSplit(String str, char split) {
        List<String> vals = new ArrayList<>();
        StringBuilder buf = new StringBuilder(); // current buffer
        boolean escaped = false; // whether the prior char was ESCAPE_CHAR
        for (char c : str.toCharArray()) {
            if (escaped) {
                // last char was a backslash
                if (c == split) {
                    // hit escaped split. pass through split char without splitting
                    buf.append(c);
                } else {
                    // hit escaped other char. pass through prior backslash and this other char
                    buf.append(ESCAPE_CHAR);
                    buf.append(c);
                }
                escaped = false;
            } else if (c == ESCAPE_CHAR) {
                // this char is a backslash. wait until next char before doing anything
                escaped = true;
            } else if (c == split) {
                // hit unescaped split. flush/reset buffer.
                vals.add(buf.toString().trim());
                buf = new StringBuilder();
            } else {
                // hit unescaped char. pass through.
                buf.append(c);
            }
        }
        if (escaped) {
            // special case for backslash at end of string: pass through
            buf.append(ESCAPE_CHAR);
        }
        vals.add(buf.toString().trim());
        return vals;
    }

    /**
     * Interface for generating a PlacementRule for a given marathon operator such as UNIQUE or CLUSTER.
     */
    private interface Operator {
        public PlacementRule run(String fieldName, String operatorName, Optional<String> parameter) throws IOException;
    }

    /**
     * {@code UNIQUE} tells Marathon to enforce uniqueness of the attribute across all of an app's
     * tasks. For example the following constraint ensures that there is only one app task running
     * on each host: {@code [["hostname", "UNIQUE"]]}
     */
    private static class UniqueOperator implements Operator {
        public PlacementRule run(String fieldName, String operatorName, Optional<String> ignoredParameter) {
            if (isHostname(fieldName)) {
                return new MaxPerHostnameRule(1);
            } else {
                // Ensure that:
                // - Task sticks to nodes with matching fieldName defined at all (AttributeRule)
                // - Task doesn't exceed one instance on those nodes (MaxPerAttributeRule)
                StringMatcher matcher = RegexMatcher.createAttribute(fieldName, ".*");
                return new AndRule(
                        AttributeRule.require(matcher), new MaxPerAttributeRule(1, matcher));
            }
        }
    }

    /**
     * {@code CLUSTER} allows you to run all of your app's tasks on agent nodes that share a certain
     * attribute. This is useful for example if you have apps with special hardware needs, or if you
     * want to run them on the same rack for low latency: {@code [["rack_id", "CLUSTER", "rack-1"]]}
     *
     * You can also use this attribute to tie an application to a specific node by using the
     * hostname property: {@code [["hostname", "CLUSTER", "a.specific.node.com"]]}
     */
    private static class ClusterOperator implements Operator {
        public PlacementRule run(String fieldName, String operatorName, Optional<String> requiredParameter)
                throws IOException {
            String parameter = validateRequiredParameter(operatorName, requiredParameter);
            if (isHostname(fieldName)) {
                return HostnameRule.require(ExactMatcher.create(parameter));
            } else {
                return AttributeRule.require(ExactMatcher.createAttribute(fieldName, parameter));
            }
        }
    }

    /**
     * {@code GROUP_BY} can be used to distribute tasks evenly across racks or datacenters for high
     * availability: {@code [["rack_id", "GROUP_BY"]]}
     *
     * Marathon only knows about different values of the attribute (e.g. "rack_id") by analyzing
     * incoming offers from Mesos. If tasks are not already spread across all possible values,
     * specify the number of values in constraints. If you don't specify the number of values, you
     * might find that the tasks are only distributed in one value, even though you are using the
     * {@code GROUP_BY} constraint. For example, if you are spreading across 3 racks, use:
     * {@code [["rack_id", "GROUP_BY", "3"]]}
     *
     * ---
     *
     * TLDR: The idea is to round-robin tasks during rollout across N distinct values (with N
     * provided by the user). For example, avoid placing 2 instances against a given rack value
     * until *all* racks have 1 instance. If N is unspecified, we will just make a best-effort
     * attempt of comparing an incoming offer against the launched tasks.
     */
    private static class GroupByOperator implements Operator {
        public PlacementRule run(String fieldName, String operatorName, Optional<String> parameter)
                throws IOException {
            final Optional<Integer> num;
            try {
                num = Optional.ofNullable(parameter.isPresent() ?
                        Integer.parseInt(parameter.get()) : null);
            } catch (NumberFormatException e) {
                throw new IOException(String.format(
                        "Unable to parse max parameter as integer for '%s' operation: %s",
                        operatorName, parameter), e);
            }
            if (isHostname(fieldName)) {
                return new RoundRobinByHostnameRule(num);
            } else {
                return new RoundRobinByAttributeRule(fieldName, num);
            }
        }
    }

    /**
     * {@code LIKE} accepts a regular expression as parameter, and allows you to run your tasks only
     * on the agent nodes whose field values match the regular expression:
     * {@code [["rack_id", "LIKE", "rack-[1-3]"]]}
     *
     * Note, the parameter is required, or you'll get a warning.
     */
    private static class LikeOperator implements Operator {
        public PlacementRule run(String fieldName, String operatorName, Optional<String> requiredParameter)
                throws IOException {
            String parameter = validateRequiredParameter(operatorName, requiredParameter);
            if (isHostname(fieldName)) {
                return HostnameRule.require(RegexMatcher.create(parameter));
            } else {
                return AttributeRule.require(RegexMatcher.createAttribute(fieldName, parameter));
            }
        }
    }

    /**
     * Just like {@code LIKE} operator, but only run tasks on agent nodes whose field values don't
     * match the regular expression: {@code [["rack_id", "UNLIKE", "rack-[7-9]"]]}
     *
     * Note, the parameter is required, or you'll get a warning.
     */
    private static class UnlikeOperator implements Operator {
        public PlacementRule run(String fieldName, String operatorName, Optional<String> requiredParameter)
                throws IOException {
            String parameter = validateRequiredParameter(operatorName, requiredParameter);
            if (isHostname(fieldName)) {
                return HostnameRule.avoid(RegexMatcher.create(parameter));
            } else {
                return AttributeRule.avoid(RegexMatcher.createAttribute(fieldName, parameter));
            }
        }
    }

    /**
     * {@code MAX_PER} accepts a number as parameter which specifies the maximum size of each group.
     * It can be used to limit tasks across racks or datacenters: {@code [["rack_id", "MAX_PER", "2"]]}
     *
     * Note, the parameter is required, or you'll get a warning.
     */
    private static class MaxPerOperator implements Operator {
        public PlacementRule run(String fieldName, String operatorName, Optional<String> requiredParameter)
                throws IOException {
            final int max;
            try {
                max = Integer.parseInt(validateRequiredParameter(operatorName, requiredParameter));
            } catch (NumberFormatException e) {
                throw new IOException(String.format(
                        "Unable to parse max parameter as integer for '%s' operation: %s",
                        operatorName, requiredParameter), e);
            }

            if (isHostname(fieldName)) {
                return new MaxPerHostnameRule(max);
            } else {
                // Ensure that:
                // - Task sticks to nodes with matching fieldName defined at all (AttributeRule)
                // - Task doesn't exceed one instance on those nodes (MaxPerAttributeRule)
                StringMatcher matcher = RegexMatcher.createAttribute(fieldName, ".*");
                return new AndRule(
                        AttributeRule.require(matcher), new MaxPerAttributeRule(max, matcher));
            }
        }
    }

    private static boolean isHostname(String fieldName) {
        return HOSTNAME_FIELD.equalsIgnoreCase(fieldName);
    }

    private static String validateRequiredParameter(
            String operatorName, Optional<String> requiredParameter) throws IOException {
        if (!requiredParameter.isPresent()) {
            throw new IOException(String.format("Missing required parameter for operator '%s'.", operatorName));
        }
        return requiredParameter.get();
    }
}
