package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import jersey.repackaged.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Implements support for generating {@link PlacementRule}s from Marathon-style constraint strings.
 *
 * @see <a href="https://mesosphere.github.io/marathon/docs/constraints.html">Marathon Constraints</a>
 */
public class MarathonConstraintParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarathonConstraintParser.class);
    private static final char ESCAPE_CHAR = '\\';

    private static final Map<String, Operator> SUPPORTED_OPERATORS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        SUPPORTED_OPERATORS.put("UNIQUE", new UniqueOperator());
        SUPPORTED_OPERATORS.put("CLUSTER", new ClusterOperator());
        SUPPORTED_OPERATORS.put("GROUP_BY", new GroupByOperator());
        SUPPORTED_OPERATORS.put("LIKE", new LikeOperator());
        SUPPORTED_OPERATORS.put("UNLIKE", new UnlikeOperator());
        SUPPORTED_OPERATORS.put("MAX_PER", new MaxPerOperator());
        SUPPORTED_OPERATORS.put("IS", new IsOperator());
    }

    private MarathonConstraintParser() {
        // do not instantiate
    }

    /**
     * ANDs the provided marathon-style constraint string onto the provided hard-coded
     * {@link PlacementRule}, or returns the provided {@link PlacementRule} as-is if the
     * marathon-style constraint is {@code null} or empty.
     *
     * @param podName The task type these constraints apply to (e.g. "hello"). Applying a constraint to all tasks
     * in a service is not supported.
     * @param rule The hard-coded {@link PlacementRule}
     * @param marathonConstraints the marathon-style constraint string, containing one or more
     * nested json list entries of the form {@code [["multi","list","value"],["hello","hi"]]},
     * or one or more colon-separated entries of the form {@code multi:list:value,hello:hi},
     * or a {@code null} or empty value if no constraint is defined
     * @throws IOException if {@code marathonConstraints} couldn't be parsed, or if the parsed
     * content isn't valid or supported
     */
    public static PlacementRule parseWith(String podName, PlacementRule rule, String marathonConstraints)
            throws IOException {
        PlacementRule marathonRule = parse(podName, marathonConstraints);
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
     *
     * @param podName The task type these constraints apply to (e.g. "hello"). Applying a constraint to all tasks
     *     in a service is not supported.
     * @param marathonConstraints the marathon-style constraint string, containing one or more
     *     nested json list entries of the form {@code [["multi","list","value"],["hello","hi"]]},
     *     or one or more colon-separated entries of the form {@code multi:list:value,hello:hi},
     *     or a {@code null} or empty value if no constraint is defined
     * @throws IOException if {@code marathonConstraints} couldn't be parsed, or if the parsed
     *     content isn't valid or supported
     */
    public static PlacementRule parse(String podName, String marathonConstraints) throws IOException {
        if (marathonConstraints == null || marathonConstraints.isEmpty() || marathonConstraints.equals("[]")) {
            // nothing to enforce
            return new PassthroughRule();
        }
        List<List<String>> rows = splitConstraints(marathonConstraints);
        StringMatcher taskFilter = RegexMatcher.create(podName + "-.*");
        if (rows.size() == 1) {
            // skip AndRule:
            return parseRow(taskFilter, rows.get(0));
        }
        List<PlacementRule> rowRules = new ArrayList<>();
        for (List<String> row : rows) {
            rowRules.add(parseRow(taskFilter, row));
        }
        return new AndRule(rowRules);
    }

    /**
     * Converts the provided marathon constraint entry to a PlacementRule.
     *
     *
     * @param taskFilter The filter to apply across all tasks to limit the scope of the constraints
     * (e.g. to a particular task type)
     * @param row a list with size 2 or 3
     * @throws IOException if the provided constraint entry is invalid
     */
    private static PlacementRule parseRow(StringMatcher taskFilter, List<String> row) throws IOException {
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
        PlacementRule rule = operator.run(taskFilter, fieldName, operatorName, parameter);
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
     * Interface for generating a PlacementRule for a given Marathon operator such as UNIQUE or CLUSTER
     * across a filtered set of tasks.
     */
    private interface Operator {
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> parameter) throws IOException;
    }

    /**
     * {@code IS} tells Marathon to match the value of the key exactly.  For example the following constraint
     * ensures that tasks only run on agents with the attribute "foo" equal to "bar": {@code [["foo", "IS", "bar"]]}
     */
    private static class IsOperator implements Operator {
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> requiredParameter) throws IOException {

            String parameter = validateRequiredParameter(operatorName, requiredParameter);
            switch (PlacementUtils.getField(fieldName)) {
                case HOSTNAME:
                    return HostnameRuleFactory.getInstance().require(ExactMatcher.create(parameter));
                case ZONE:
                    return ZoneRuleFactory.getInstance().require(ExactMatcher.create(parameter));
                case REGION:
                    return RegionRuleFactory.getInstance().require(ExactMatcher.create(parameter));
                case ATTRIBUTE:
                    return AttributeRuleFactory.getInstance().require(
                            ExactMatcher.createAttribute(fieldName, parameter));
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown LIKE placement type encountered: %s", fieldName));
            }
        }
    }

    /**
     * {@code UNIQUE} tells Marathon to enforce uniqueness of the attribute across all
     * tasks of a given type. For example the following constraint ensures that there is only one app task running
     * on each host for some task type: {@code [["hostname", "UNIQUE"]]}
     */
    private static class UniqueOperator implements Operator {
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> ignoredParameter) {

            switch (PlacementUtils.getField(fieldName)) {
                case HOSTNAME:
                    return new MaxPerHostnameRule(1, taskFilter);
                case ZONE:
                    return new MaxPerZoneRule(1, taskFilter);
                case REGION:
                    return new MaxPerRegionRule(1, taskFilter);
                case ATTRIBUTE:
                    StringMatcher matcher = RegexMatcher.createAttribute(fieldName, ".*");
                    return new AndRule(
                            AttributeRuleFactory.getInstance().require(matcher),
                            new MaxPerAttributeRule(1, matcher, taskFilter));
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown UNIQUE placement type encountered: %s", fieldName));
            }
        }
    }

    /**
     * {@code CLUSTER} allows you to run all tasks of a given task type on agent nodes that share a certain
     * attribute. This is useful for example if you have apps with special hardware needs, or if you
     * want to run them on the same rack for low latency: {@code [["rack_id", "CLUSTER", "rack-1"]]}
     *
     * You can also use this attribute to tie a task type to a specific node by using the
     * hostname property: {@code [["hostname", "CLUSTER", "a.specific.node.com"]]}
     */
    private static class ClusterOperator implements Operator {
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> requiredParameter) throws IOException {

            String parameter = validateRequiredParameter(operatorName, requiredParameter);

            switch (PlacementUtils.getField(fieldName)) {
                case HOSTNAME:
                    return HostnameRuleFactory.getInstance().require(ExactMatcher.create(parameter));
                case ZONE:
                    return ZoneRuleFactory.getInstance().require(ExactMatcher.create(parameter));
                case REGION:
                    return RegionRuleFactory.getInstance().require(ExactMatcher.create(parameter));
                case ATTRIBUTE:
                    return AttributeRuleFactory.getInstance().require(
                            ExactMatcher.createAttribute(fieldName, parameter));
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown CLUSTER placement type encountered: %s", fieldName));
            }
        }
    }

    /**
     * {@code GROUP_BY} can be used to distribute tasks of a given task type evenly across racks or datacenters for high
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
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> parameter) throws IOException {

            final Optional<Integer> num;
            try {
                num = Optional.ofNullable(parameter.isPresent() ?
                        Integer.parseInt(parameter.get()) : null);
            } catch (NumberFormatException e) {
                throw new IOException(String.format(
                        "Unable to parse max parameter as integer for '%s' operation: %s",
                        operatorName, parameter), e);
            }

            switch (PlacementUtils.getField(fieldName)) {
                case HOSTNAME:
                    return new RoundRobinByHostnameRule(num, taskFilter);
                case ZONE:
                    return new RoundRobinByZoneRule(num, taskFilter);
                case REGION:
                    return new RoundRobinByRegionRule(num, taskFilter);
                case ATTRIBUTE:
                    return new RoundRobinByAttributeRule(fieldName, num, taskFilter);
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown GROUP_BY placement type encountered: %s", fieldName));
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
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> requiredParameter) throws IOException {

            String parameter = validateRequiredParameter(operatorName, requiredParameter);
            switch (PlacementUtils.getField(fieldName)) {
                case HOSTNAME:
                    return HostnameRuleFactory.getInstance().require(RegexMatcher.create(parameter));
                case ZONE:
                    return ZoneRuleFactory.getInstance().require(RegexMatcher.create(parameter));
                case REGION:
                    return RegionRuleFactory.getInstance().require(RegexMatcher.create(parameter));
                case ATTRIBUTE:
                    return AttributeRuleFactory.getInstance().require(
                            RegexMatcher.createAttribute(fieldName, parameter));
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown LIKE placement type encountered: %s", fieldName));
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
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> requiredParameter) throws IOException {

            String parameter = validateRequiredParameter(operatorName, requiredParameter);
            switch (PlacementUtils.getField(fieldName)) {
                case HOSTNAME:
                    return HostnameRuleFactory.getInstance().avoid(RegexMatcher.create(parameter));
                case ZONE:
                    return ZoneRuleFactory.getInstance().avoid(RegexMatcher.create(parameter));
                case REGION:
                    return RegionRuleFactory.getInstance().avoid(RegexMatcher.create(parameter));
                case ATTRIBUTE:
                    return AttributeRuleFactory.getInstance().avoid(RegexMatcher.createAttribute(fieldName, parameter));
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown UNLIKE placement type encountered: %s", fieldName));
            }
        }
    }

    /**
     * {@code MAX_PER} accepts a number as parameter which specifies the maximum size of each group.
     * It can be used to limit tasks of a given task type across racks or
     * datacenters: {@code [["rack_id", "MAX_PER", "2"]]}
     *
     * Note, the parameter is required, or you'll get a warning.
     */
    private static class MaxPerOperator implements Operator {
        public PlacementRule run(
                StringMatcher taskFilter,
                String fieldName,
                String operatorName,
                Optional<String> requiredParameter) throws IOException {

            final int max;
            try {
                max = Integer.parseInt(validateRequiredParameter(operatorName, requiredParameter));
            } catch (NumberFormatException e) {
                throw new IOException(String.format(
                        "Unable to parse max parameter as integer for '%s' operation: %s",
                        operatorName, requiredParameter), e);
            }

            switch (PlacementUtils.getField(fieldName)) {
                case HOSTNAME:
                    return new MaxPerHostnameRule(max, taskFilter);
                case ZONE:
                    return new MaxPerZoneRule(max, taskFilter);
                case REGION:
                    return new MaxPerRegionRule(max, taskFilter);
                case ATTRIBUTE:
                    // Ensure that:
                    // - Task sticks to nodes with matching fieldName defined at all (AttributeRule)
                    // - Task doesn't exceed one instance on those nodes (MaxPerAttributeRule)
                    StringMatcher matcher = RegexMatcher.createAttribute(fieldName, ".*");
                    return new AndRule(
                            AttributeRuleFactory.getInstance()
                                    .require(matcher), new MaxPerAttributeRule(max, matcher, taskFilter));
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown MAX_PER placement type encountered: %s", fieldName));
            }
        }
    }

    private static String validateRequiredParameter(
            String operatorName, Optional<String> requiredParameter) throws IOException {
        if (!requiredParameter.isPresent()) {
            throw new IOException(String.format("Missing required parameter for operator '%s'.", operatorName));
        }
        return requiredParameter.get();
    }
}
