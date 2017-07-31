package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.Protos.Value.Range;
import org.apache.mesos.Protos.Value.Type;

import java.util.Collections;
import java.util.List;

/**
 * Utilities for manipulating Value protobufs.
 */
public class ValueUtils {
    public static Value getValue(Resource resource) {
        Type type = resource.getType();
        Value.Builder builder = Value.newBuilder();
        builder.setType(type);

        switch (type) {
            case SCALAR:
                return builder.setScalar(resource.getScalar()).build();
            case RANGES:
                return builder.setRanges(resource.getRanges()).build();
            case SET:
                return builder.setSet(resource.getSet()).build();
            default:
                return null;
        }
    }

    public static Value add(Value val1, Value val2) {
        Type type1 = val1.getType();
        Type type2 = val2.getType();

        if (type1 != type2) {
            return null;
        }

        switch (type1) {
            case SCALAR:
                Value.Scalar scalar = add(val1.getScalar(), val2.getScalar());
                return Value.newBuilder().setType(type1).setScalar(scalar).build();
            case RANGES:
                Value.Ranges ranges = add(val1.getRanges(), val2.getRanges());
                return Value.newBuilder().setType(type1).setRanges(ranges).build();
            default:
                return null;
        }
    }

    private static Value.Scalar add(Value.Scalar scal1, Value.Scalar scal2) {
        double value = scal1.getValue() + scal2.getValue();
        return Value.Scalar.newBuilder().setValue(value).build();
    }

    private static Value.Ranges add(Value.Ranges ranges1, Value.Ranges ranges2) {
        List<Range> list1 = ranges1.getRangeList();
        List<Range> list2 = ranges2.getRangeList();
        List<Range> list = RangeUtils.mergeRanges(list1, list2);
        return Value.Ranges.newBuilder().addAllRange(list).build();
    }

    public static Value subtract(Value val1, Value val2) {
        Type type1 = val1.getType();
        Type type2 = val2.getType();

        if (type1 != type2) {
            return null;
        }

        switch (type1) {
            case SCALAR:
                Value.Scalar scalar = subtract(val1.getScalar(), val2.getScalar());
                return Value.newBuilder().setType(type1).setScalar(scalar).build();
            case RANGES:
                Value.Ranges ranges = subtract(val1.getRanges(), val2.getRanges());
                return Value.newBuilder().setType(type1).setRanges(ranges).build();
            default:
                return null;
        }
    }

    private static Value.Scalar subtract(Value.Scalar scal1, Value.Scalar scal2) {
        double value = scal1.getValue() - scal2.getValue();
        return Value.Scalar.newBuilder().setValue(value).build();
    }

    private static Value.Ranges subtract(Value.Ranges ranges1, Value.Ranges ranges2) {
        List<Range> list1 = ranges1.getRangeList();
        List<Range> list2 = ranges2.getRangeList();
        List<Range> list = RangeUtils.subtractRanges(list1, list2);
        return Value.Ranges.newBuilder().addAllRange(list).build();
    }

    public static boolean equal(Value val1, Value val2) {
        return compare(val1, val2) == 0;
    }

    public static Integer compare(Value val1, Value val2) {
        Type type1 = val1.getType();
        Type type2 = val2.getType();

        if (type1 != type2) {
            return null;
        }

        switch (type1) {
            case SCALAR:
                return compare(val1.getScalar(), val2.getScalar());
            case RANGES:
                return compare(val1.getRanges(), val2.getRanges());
            default:
                return null;
        }
    }

    public static Boolean isPositive(Value value) {
        return compare(value, getZero(value.getType())) > 0;
    }

    private static Integer compare(Value.Scalar scal1, Value.Scalar scal2) {
        double val1 = scal1.getValue();
        double val2 = scal2.getValue();

        if (val1 < val2) {
            return -1;
        } else if (val1 > val2) {
            return 1;
        } else {
            return 0;
        }
    }

    private static Integer compare(Value.Ranges ranges1, Value.Ranges ranges2) {
        List<Range> list1 = ranges1.getRangeList();
        List<Range> list2 = ranges2.getRangeList();

        if (RangeUtils.rangesEqual(list1, list2)) {
            return 0;
        } else if (RangeUtils.subtractRanges(list1, list2).size() == 0) {
            return -1;
        } else {
            return 1;
        }
    }

    public static Value getZero(Value.Type type) {
        switch (type) {
            case SCALAR:
                Value.Scalar scalar = Value.Scalar.newBuilder().setValue(0).build();
                return Value.newBuilder().setType(type).setScalar(scalar).build();
            case RANGES:
                Value.Ranges ranges = Value.Ranges.newBuilder().addAllRange(Collections.emptyList()).build();
                return Value.newBuilder().setType(type).setRanges(ranges).build();
            default:
                return null;
        }
    }
}
