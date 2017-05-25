package com.mesosphere.sdk.offer;

import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.apache.mesos.Protos.Value.Range;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for commonly needed algorithms for Mesos frameworks.
 */
public final class RangeAlgorithms {

    private RangeAlgorithms() {
        // do not instantiate
    }

    /**
     * Combines and flattens the provided sets of ranges into a unified set.
     */
    public static List<Range> mergeRanges(List<Range> r1, List<Range> r2) {
        List<Interval> intervals = rangesToIntervals(r1);
        intervals.addAll(rangesToIntervals(r2));
        return intervalsToRanges(intervalsToIntervalSet(intervals).getIntervals());
    }

    /**
     * Removes the range intervals listed in {@code subtrahend} from {@code minuend}.
     */
    public static List<Range> subtractRanges(List<Range> minuend, List<Range> subtrahend) {
        IntervalSet iMinuend = intervalsToIntervalSet(rangesToIntervals(minuend));
        IntervalSet iSubtrahend = intervalsToIntervalSet(rangesToIntervals(subtrahend));
        IntervalSet iDifference = IntervalSet.subtract(iMinuend, iSubtrahend);
        return intervalSetToRanges(iDifference);
    }

    /**
     * Returns whether the provided sets of ranges are equivalent when any overlaps are flattened.
     */
    public static boolean rangesEqual(List<Range> list1, List<Range> list2) {
        IntervalSet i1 = intervalsToIntervalSet(rangesToIntervals(list1));
        IntervalSet i2 = intervalsToIntervalSet(rangesToIntervals(list2));
        return i1.equals(i2);
    }

    private static Interval rangeToInterval(Range range) {
        return Interval.of((int) range.getBegin(), (int) range.getEnd());
    }

    private static List<Interval> rangesToIntervals(List<Range> ranges) {
        List<Interval> intervals = new ArrayList<Interval>();
        for (Range range : ranges) {
            intervals.add(rangeToInterval(range));
        }
        return intervals;
    }

    private static List<Range> intervalsToRanges(List<Interval> intervals) {
        List<Range> ranges = new ArrayList<Range>();
        for (Interval interval : intervals) {
            ranges.add(Range.newBuilder().setBegin(interval.a).setEnd(interval.b).build());
        }
        return ranges;
    }

    private static IntervalSet intervalsToIntervalSet(List<Interval> intervals) {
        IntervalSet intervalSet = new IntervalSet();
        for (Interval interval : intervals) {
            intervalSet.add(interval.a, interval.b);
        }
        return intervalSet;
    }

    private static List<Range> intervalSetToRanges(IntervalSet intervalSet) {
        return intervalSet.isNil()
                ? new ArrayList<Range>()
                : intervalsToRanges(intervalSet.getIntervals());
    }
}
