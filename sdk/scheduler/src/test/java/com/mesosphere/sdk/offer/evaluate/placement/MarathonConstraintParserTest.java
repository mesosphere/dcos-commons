package com.mesosphere.sdk.offer.evaluate.placement;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link MarathonConstraintParser}.
 */
public class MarathonConstraintParserTest {
    static final String POD_NAME = "hello";

    @Test
    public void testSplitConstraints() throws IOException {
        assertEquals(Arrays.asList(Arrays.asList("")),
                MarathonConstraintParser.splitConstraints(""));
        assertEquals(Arrays.asList(Arrays.asList("a")),
                MarathonConstraintParser.splitConstraints("a"));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c")),
                MarathonConstraintParser.splitConstraints(unescape("['a', 'b', 'c']")));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c")),
                MarathonConstraintParser.splitConstraints(unescape("[['a', 'b', 'c']]")));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c"), Arrays.asList("d", "e")),
                MarathonConstraintParser.splitConstraints(unescape("[['a', 'b', 'c'], ['d', 'e']]")));
        assertEquals(Arrays.asList(Arrays.asList("a"), Collections.emptyList()),
                MarathonConstraintParser.splitConstraints(unescape("[['a'], []]")));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c"), Arrays.asList("d", "e"), Arrays.asList("f", "g", "")),
                MarathonConstraintParser.splitConstraints("a:b:c,d:e,f:g:"));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c"), Arrays.asList("", "d", "e"), Arrays.asList("", "f", "")),
                MarathonConstraintParser.splitConstraints("a:b:c,:d:e,:f:"));
        assertEquals(Arrays.asList(Arrays.asList("a", "b", "c"), Arrays.asList("", "d", "e"), Arrays.asList("", "f", "")),
                MarathonConstraintParser.splitConstraints(" a : b : c , : d : e , : f : "));
        assertEquals(Arrays.asList(Arrays.asList("", "", ""), Arrays.asList("", "")),
                MarathonConstraintParser.splitConstraints("::,:"));
    }

    @Test
    public void testUniqueOperator() throws IOException {
        // example from marathon documentation:
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['hostname', 'UNIQUE']]")).toString();
        assertEquals("MaxPerHostnameRule{max=1, task-filter=RegexMatcher{pattern='hello-.*'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['hostname', 'UNIQUE']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "hostname:UNIQUE").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'UNIQUE']]")).toString();
        assertEquals("AndRule{rules=[AttributeRule{matcher=RegexMatcher{pattern='rack-id:.*'}}, " +
                "MaxPerAttributeRule{max=1, matcher=RegexMatcher{pattern='rack-id:.*'}, task-filter=RegexMatcher{pattern='hello-.*'}}]}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['rack-id', 'UNIQUE']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "rack-id:UNIQUE").toString());
    }

    @Test
    public void testClusterOperator() throws IOException {
        // example from marathon documentation:
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'CLUSTER', 'rack-1']]")).toString();
        assertEquals("AttributeRule{matcher=ExactMatcher{str='rack-id:rack-1'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['rack-id', 'CLUSTER', 'rack-1']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "rack-id:CLUSTER:rack-1").toString());

        // example from marathon documentation:
        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['hostname', 'CLUSTER', 'a.specific.node.com']]")).toString();
        assertEquals("HostnameRule{matcher=ExactMatcher{str='a.specific.node.com'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['hostname', 'CLUSTER', 'a.specific.node.com']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "hostname:CLUSTER:a.specific.node.com").toString());
    }

    @Test
    public void testGroupByOperator() throws IOException {
        // example from marathon documentation:
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'GROUP_BY']]")).toString();
        assertEquals("RoundRobinByAttributeRule{attribute=rack-id, attribute-count=Optional.empty, task-filter=RegexMatcher{pattern='hello-.*'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['rack-id', 'GROUP_BY']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "rack-id:GROUP_BY").toString());

        // example from marathon documentation:
        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'GROUP_BY', '3']]")).toString();
        assertEquals("RoundRobinByAttributeRule{attribute=rack-id, attribute-count=Optional[3], task-filter=RegexMatcher{pattern='hello-.*'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['rack-id', 'GROUP_BY', '3']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "rack-id:GROUP_BY:3").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['zone', 'GROUP_BY', '3']]")).toString();
        assertEquals("RoundRobinByAttributeRule{attribute=zone, attribute-count=Optional[3], task-filter=RegexMatcher{pattern='hello-.*'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['zone', 'GROUP_BY', '3']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "zone:GROUP_BY:3").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['hostname', 'GROUP_BY']]")).toString();
        assertEquals("RoundRobinByHostnameRule{agent-count=Optional.empty, task-filter=RegexMatcher{pattern='hello-.*'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['hostname', 'GROUP_BY']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "hostname:GROUP_BY").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['hostname', 'GROUP_BY', '3']]")).toString();
        assertEquals("RoundRobinByHostnameRule{agent-count=Optional[3], task-filter=RegexMatcher{pattern='hello-.*'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['hostname', 'GROUP_BY', '3']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "hostname:GROUP_BY:3").toString());
    }

    @Test
    public void testLikeOperator() throws IOException {
        // example from marathon documentation:
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'LIKE', 'rack-[1-3]']]")).toString();
        assertEquals("AttributeRule{matcher=RegexMatcher{pattern='rack-id:rack-[1-3]'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['rack-id', 'LIKE', 'rack-[1-3]']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "rack-id:LIKE:rack-[1-3]").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['hostname', 'LIKE', 'rack-[1-3]']]")).toString();
        assertEquals("HostnameRule{matcher=RegexMatcher{pattern='rack-[1-3]'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['hostname', 'LIKE', 'rack-[1-3]']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "hostname:LIKE:rack-[1-3]").toString());
    }

    @Test
    public void testIsOperator() throws IOException {
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['foo', 'IS', 'bar']]")).toString();
        assertEquals("AttributeRule{matcher=ExactMatcher{str='foo:bar'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['foo', 'IS', 'bar']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "foo:IS:bar").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['@region', 'IS', 'bar']]")).toString();
        assertEquals("RegionRule{matcher=ExactMatcher{str='bar'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['@region', 'IS', 'bar']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "@region:IS:bar").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['@zone', 'IS', 'bar']]")).toString();
        assertEquals("ZoneRule{matcher=ExactMatcher{str='bar'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['@zone', 'IS', 'bar']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "@zone:IS:bar").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['@hostname', 'IS', 'bar']]")).toString();
        assertEquals("HostnameRule{matcher=ExactMatcher{str='bar'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['@hostname', 'IS', 'bar']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "@hostname:IS:bar").toString());
    }

    @Test
    public void testUnlikeOperator() throws IOException {
        // example from marathon documentation:
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'UNLIKE', 'rack-[7-9]']]")).toString();
        assertEquals("NotRule{rule=AttributeRule{matcher=RegexMatcher{pattern='rack-id:rack-[7-9]'}}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['rack-id', 'UNLIKE', 'rack-[7-9]']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "rack-id:UNLIKE:rack-[7-9]").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['hostname', 'UNLIKE', 'rack-[7-9]']]")).toString();
        assertEquals("NotRule{rule=HostnameRule{matcher=RegexMatcher{pattern='rack-[7-9]'}}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['hostname', 'UNLIKE', 'rack-[7-9]']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "hostname:UNLIKE:rack-[7-9]").toString());
    }

    @Test
    public void testMaxPerOperator() throws IOException {
        // example from marathon documentation:
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'MAX_PER', '2']]")).toString();
        assertEquals("AndRule{rules=[AttributeRule{matcher=RegexMatcher{pattern='rack-id:.*'}}, " +
                "MaxPerAttributeRule{max=2, matcher=RegexMatcher{pattern='rack-id:.*'}, task-filter=RegexMatcher{pattern='hello-.*'}}]}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['rack-id', 'MAX_PER', '2']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "rack-id:MAX_PER:2").toString());

        constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape("[['hostname', 'MAX_PER', '2']]")).toString();
        assertEquals("MaxPerHostnameRule{max=2, task-filter=RegexMatcher{pattern='hello-.*'}}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, unescape("['hostname', 'MAX_PER', '2']")).toString());
        assertEquals(constraintStr, MarathonConstraintParser.parse(POD_NAME, "hostname:MAX_PER:2").toString());
    }

    @Test
    public void testManyOperators() throws IOException {
        String constraintStr = MarathonConstraintParser.parse(POD_NAME, unescape(
                "[['hostname', 'UNIQUE'], "
                + "['rack-id', 'CLUSTER', 'rack-1'], "
                + "['rack-id', 'GROUP_BY'], "
                + "['rack-id', 'LIKE', 'rack-[1-3]'], "
                + "['rack-id', 'UNLIKE', 'rack-[7-9]'],"
                + "['rack-id', 'MAX_PER', '2']]")).toString();
        assertEquals("AndRule{"
                + "rules=[MaxPerHostnameRule{max=1, task-filter=RegexMatcher{pattern='hello-.*'}}, "
                + "AttributeRule{matcher=ExactMatcher{str='rack-id:rack-1'}}, "
                + "RoundRobinByAttributeRule{attribute=rack-id, attribute-count=Optional.empty, task-filter=RegexMatcher{pattern='hello-.*'}}, "
                + "AttributeRule{matcher=RegexMatcher{pattern='rack-id:rack-[1-3]'}}, "
                + "NotRule{rule=AttributeRule{matcher=RegexMatcher{pattern='rack-id:rack-[7-9]'}}}, "
                + "AndRule{rules=["
                + "AttributeRule{matcher=RegexMatcher{pattern='rack-id:.*'}}, "
                + "MaxPerAttributeRule{max=2, matcher=RegexMatcher{pattern='rack-id:.*'}, task-filter=RegexMatcher{pattern='hello-.*'}}]}]}", constraintStr);
        assertEquals(constraintStr, MarathonConstraintParser.parse(
                POD_NAME, "hostname:UNIQUE,"
                + "rack-id:CLUSTER:rack-1,"
                + "rack-id:GROUP_BY,"
                + "rack-id:LIKE:rack-[1-3],"
                + "rack-id:UNLIKE:rack-[7-9],"
                + "rack-id:MAX_PER:2").toString());
    }

    @Test
    public void testEscapedCommaRegex() throws IOException {
        assertEquals("AttributeRule{matcher=RegexMatcher{pattern='rack-id:rack-{1,3}'}}",
            MarathonConstraintParser.parse(POD_NAME, "rack-id:LIKE:rack-{1\\,3}").toString());
    }

    @Test
    public void testEscapedColonRegex() throws IOException {
        assertEquals("AttributeRule{matcher=RegexMatcher{pattern='rack-id:foo:bar:baz'}}",
            MarathonConstraintParser.parse(POD_NAME, "rack-id:LIKE:foo\\:bar\\:baz").toString());
    }

    @Test
    public void testEmptyConstraint() throws IOException {
        assertEquals("PassthroughRule{}", MarathonConstraintParser.parse(POD_NAME, "").toString());
    }

    @Test
    public void testEmptyArrayConstraint() throws IOException {
        assertEquals("PassthroughRule{}", MarathonConstraintParser.parse(POD_NAME, "[]").toString());
    }

    @Test(expected = IOException.class)
    public void testBadListConstraint() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, unescape("[['rack-id', 'MAX_PER', '2'")); // missing ']]'
    }

    @Test(expected = IOException.class)
    public void testBadFlatConstraint() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:MAX_PER:,"); // missing last elem
    }

    @Test(expected = IOException.class)
    public void testBadParamGroupBy() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:GROUP_BY:foo"); // expected int
    }

    @Test(expected = IOException.class)
    public void testBadParamMaxPer() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:MAX_PER:foo"); // expected int
    }

    @Test(expected = IOException.class)
    public void testMissingParamCluster() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:CLUSTER"); // expected param
    }

    @Test(expected = IOException.class)
    public void testMissingParamLike() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:LIKE"); // expected param
    }

    @Test(expected = IOException.class)
    public void testMissingParamUnlike() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:UNLIKE"); // expected param
    }

    @Test(expected = IOException.class)
    public void testMissingParamMaxPer() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:MAX_PER"); // expected param
    }

    @Test(expected = IOException.class)
    public void testUnknownCommand() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:FOO:foo");
    }

    @Test(expected = IOException.class)
    public void testTooManyElemenents() throws IOException {
        MarathonConstraintParser.parse(POD_NAME, "rack-id:LIKE:foo:bar");
    }

    @Test
    public void testSplitPlain() {
        assertEquals(Arrays.asList("hi", "hey"), MarathonConstraintParser.escapedSplit("hi,hey", ','));
    }

    @Test
    public void testSplitEscaped() {
        assertEquals(Arrays.asList("hi,hey"), MarathonConstraintParser.escapedSplit("hi\\,hey", ','));
    }

    @Test
    public void testSplitEscapedUnescaped() {
        assertEquals(Arrays.asList("hi,", "hey"), MarathonConstraintParser.escapedSplit("hi\\,,hey", ','));
    }

    @Test
    public void testSplitUnescapedEscaped() {
        assertEquals(Arrays.asList("hi", ",hey"), MarathonConstraintParser.escapedSplit("hi,\\,hey", ','));
    }

    @Test
    public void testTrimPreserveEmptyTokens() {
        assertEquals(Arrays.asList("hi", "", "", "hey"), MarathonConstraintParser.escapedSplit("hi,,   ,  hey  ", ','));
    }

    @Test
    public void testSplitEmptyString() {
        assertEquals(Arrays.asList(""), MarathonConstraintParser.escapedSplit("", ','));
    }

    @Test
    public void testSplitTrailingBackslash() {
        assertEquals(Arrays.asList("hello", "hi\\"), MarathonConstraintParser.escapedSplit("hello,hi\\", ','));
    }

    // Avoid needing \"'s throughout json strings:
    private static String unescape(String s) {
        return s.replace('\'', '"');
    }
}
