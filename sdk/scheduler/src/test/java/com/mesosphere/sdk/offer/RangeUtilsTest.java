package com.mesosphere.sdk.offer;

import java.util.Arrays;
import java.util.List;

import org.apache.mesos.Protos.Value.Range;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link RangeUtils}.
 */
public class RangeUtilsTest {

    @Test
    public void testMergingOverlappingRanges() {
        List<Range> r1 = Arrays.asList(getRange(1, 3));
        List<Range> r2 = Arrays.asList(getRange(2, 4));

        List<Range> mergedRanges = RangeUtils.mergeRanges(r1, r2);

        assertEquals(1, mergedRanges.size());
        assertEquals(1, mergedRanges.get(0).getBegin());
        assertEquals(4, mergedRanges.get(0).getEnd());
    }

    @Test
    public void testMergingNonOverlappingRanges() {
        List<Range> r1 = Arrays.asList(getRange(1, 3));
        List<Range> r2 = Arrays.asList(getRange(5, 7));

        List<Range> mergedRanges = RangeUtils.mergeRanges(r1, r2);

        assertEquals(2, mergedRanges.size());
        assertEquals(1, mergedRanges.get(0).getBegin());
        assertEquals(3, mergedRanges.get(0).getEnd());
        assertEquals(5, mergedRanges.get(1).getBegin());
        assertEquals(7, mergedRanges.get(1).getEnd());
    }

    @Test
    public void testMergingInternallyOverlappingRanges() {
        List<Range> r1 = Arrays.asList(getRange(1, 5));
        List<Range> r2 = Arrays.asList(getRange(2, 4));

        List<Range> mergedRanges = RangeUtils.mergeRanges(r1, r2);

        assertEquals(1, mergedRanges.size());
        assertEquals(1, mergedRanges.get(0).getBegin());
        assertEquals(5, mergedRanges.get(0).getEnd());
    }

    @Test
    public void testSubtractRanges() {
        List<Range> r1 = Arrays.asList(getRange(1, 3), getRange(5, 7));
        List<Range> r2 = Arrays.asList(getRange(1, 3));

        List<Range> difference = RangeUtils.subtractRanges(r1, r2);

        assertEquals(1, difference.size());
        assertEquals(5, difference.get(0).getBegin());
        assertEquals(7, difference.get(0).getEnd());
    }

    @Test
    public void testSubtractRangesForNilResult() {
        List<Range> r1 = Arrays.asList(getRange(2, 3));
        List<Range> r2 = Arrays.asList(getRange(1, 5));

        List<Range> difference = RangeUtils.subtractRanges(r1, r2);

        assertTrue(difference.isEmpty());
    }

    @Test
    public void testRangeIsInAny() {
        List<Range> r1 = Arrays.asList(getRange(1, 3), getRange(5, 7));
        assertFalse(RangeUtils.isInAny(r1, 0));
        assertTrue(RangeUtils.isInAny(r1, 1));
        assertTrue(RangeUtils.isInAny(r1, 2));
        assertTrue(RangeUtils.isInAny(r1, 3));
        assertFalse(RangeUtils.isInAny(r1, 4));
        assertTrue(RangeUtils.isInAny(r1, 5));
        assertTrue(RangeUtils.isInAny(r1, 6));
        assertTrue(RangeUtils.isInAny(r1, 7));
        assertFalse(RangeUtils.isInAny(r1, 8));

        List<Range> r2 = Arrays.asList(getRange(2, 2));
        assertFalse(RangeUtils.isInAny(r2, 1));
        assertTrue(RangeUtils.isInAny(r2, 2));
        assertFalse(RangeUtils.isInAny(r2, 3));
    }

    private static Range getRange(int begin, int end) {
        Range.Builder builder = Range.newBuilder();
        builder.setBegin(begin);
        builder.setEnd(end);
        return builder.build();
    }
}
