package com.mesosphere.sdk.offer.evaluate.placement;

import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the {@link ExactMatcher} class.
 *
 * Should match:
 *
 *   a IS a
 *
 *   100 IS 100
 *
 *   100.0 IS 100
 *
 *   100 IS 100.0
 *
 *   100 IS 100.0001
 *
 * Should NOT match:
 *
 *   [0-9] IS 0-9
 *
 *   {a} IS a
 *
 *   ab IS a
 *
 *   A IS a
 *
 *   a IS A
 */
public class ExactMatcherTest {
    @Test
    public void emptyStringMatches() {
        StringMatcher matcher = ExactMatcher.create("");
        Assert.assertTrue(matcher.matches(""));
    }

    @Test
    public void emptyStringDoesntMatchNonEmpty() {
        StringMatcher matcher = ExactMatcher.create("");
        Assert.assertFalse(matcher.matches("foo"));
    }

    @Test
    public void differentStringsDontMatch() {
        StringMatcher matcher = ExactMatcher.create("foo");
        Assert.assertFalse(matcher.matches("bar"));
    }

    @Test
    public void matchingStringsMatch() {
        StringMatcher matcher = ExactMatcher.create("foo");
        Assert.assertTrue(matcher.matches("foo"));
    }

    @Test
    public void integersMatch() {
        StringMatcher matcher = ExactMatcher.create("100");
        Assert.assertTrue(matcher.matches("100"));
    }

    @Test
    public void integerAndDoubleMatch() {
        StringMatcher matcher = ExactMatcher.create("100.0");
        Assert.assertTrue(matcher.matches("100"));

        matcher = ExactMatcher.create("100");
        Assert.assertTrue(matcher.matches("100.0"));
    }

    @Test
    public void integerAndDoubleMatchWithinEpsilon() {
        StringMatcher matcher = ExactMatcher.create("100");
        Assert.assertTrue(matcher.matches("100.00001"));
    }

    @Test
    public void integerAndDoubleMatchAtEpsilon() {
        StringMatcher matcher = ExactMatcher.create("100");
        Assert.assertTrue(matcher.matches("100.0001"));
    }

    @Test
    public void integerAndDoubleDontMatchOutsideEpsilon() {
        StringMatcher matcher = ExactMatcher.create("100");
        Assert.assertTrue(matcher.matches("100.00011"));
    }
}
