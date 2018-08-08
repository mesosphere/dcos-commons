package com.mesosphere.sdk.specification;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

/**
 * Utilities for validating members in service specs.
 */
public class ValidationUtils {

    private ValidationUtils() {
        // do not instantiate
    }

    // Collection checks

    /**
     * Throws an exception if {@code field} is empty or {@code null}.
     */
    public static void nonEmpty(Object parent, String fieldName, Collection<?> field) {
        nonNull(parent, fieldName, field);
        nonEmptyAllowNull(parent, fieldName, field);
    }

    /**
     * Throws an exception if {@code field} is empty, but not if it is {@code null}.
     */
    public static void nonEmptyAllowNull(Object parent, String fieldName, Collection<?> field) {
        if (field != null && field.isEmpty()) {
            throw new IllegalArgumentException(String.format("%s cannot be empty: %s", fieldName, parent));
        }
    }

    /**
     * Throws an exception if there are any duplicate entries in the provided list.
     */
    public static <T> void isUnique(Object parent, String fieldName, Stream<T> field) {
        Set<T> found = new HashSet<>();
        Set<T> duplicates = field.filter(e -> !found.add(e)).collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "%s cannot have any duplicates (%s): %s", fieldName, duplicates, parent));
        }
    }

    // String checks

    /**
     * Throws an exception if {@code field} is whitespace-only, empty, or {@code null}.
     */
    public static void nonBlank(Object parent, String fieldName, String field) {
        nonNull(parent, fieldName, field);
        nonBlankAllowNull(parent, fieldName, field);
    }

    /**
     * Throws an exception if {@code field} is empty or {@code null}.
     */
    public static void nonEmpty(Object parent, String fieldName, String field) {
        nonNull(parent, fieldName, field);
        nonEmptyAllowNull(parent, fieldName, field);
    }

    /**
     * Throws an exception if {@code field} is whitespace-only or empty, but not if it is {@code null}.
     */
    public static void nonBlankAllowNull(Object parent, String fieldName, String field) {
        if (field != null && StringUtils.isBlank(field)) {
            throw new IllegalArgumentException(String.format("%s cannot be blank or empty: %s", fieldName, parent));
        }
    }

    /**
     * Throws an exception if {@code field} is empty, but not if it is {@code null}.
     */
    public static void nonEmptyAllowNull(Object parent, String fieldName, String field) {
        if (field != null && field.isEmpty()) {
            throw new IllegalArgumentException(String.format("%s cannot be empty: %s", fieldName, parent));
        }
    }

    /**
     * Throws an exception if {@code field} is {@code null} or doesn't match the provided regular expression.
     */
    public static void matchesRegex(Object parent, String fieldName, String field, Pattern pattern) {
        nonNull(parent, fieldName, field);
        matchesRegexAllowNull(parent, fieldName, field, pattern);
    }

    /**
     * Throws an exception if {@code field} is doesn't match the provided regular expression, but not if it's
     * {@code null}.
     */
    public static void matchesRegexAllowNull(Object parent, String fieldName, String field, Pattern pattern) {
        if (field != null && !pattern.matcher(field).matches()) {
            throw new IllegalArgumentException(
                    String.format("%s must match pattern '%s': %s", fieldName, pattern, parent));
        }
    }

    // Integer checks

    /**
     * Throws an exception if {@code field} is {@code null} or less than {@code 0}.
     */
    public static void nonNegative(Object parent, String fieldName, Integer field) {
        nonNegative(parent, fieldName, Long.valueOf(field));
    }

    /**
     * Throws an exception if {@code field} is {@code null} or less than {@code 0}.
     */
    public static void nonNegative(Object parent, String fieldName, Long field) {
        atLeast(parent, fieldName, field, 0);
    }

    /**
     * Throws an exception if {@code field} is {@code null} or less than {@code 1}.
     */
    public static void atLeastOne(Object parent, String fieldName, Integer field) {
        atLeastOne(parent, fieldName, Long.valueOf(field));
    }

    /**
     * Throws an exception if {@code field} is {@code null} or less than {@code 1}.
     */
    public static void atLeastOne(Object parent, String fieldName, Long field) {
        atLeast(parent, fieldName, field, 1);
    }

    /**
     * Throws an exception if {@code field} is {@code null} or less than {@code minValue}.
     */
    public static void atLeast(Object parent, String fieldName, Integer field, int minValue) {
        atLeast(parent, fieldName, Long.valueOf(field), minValue);
    }

    /**
     * Throws an exception if {@code field} is {@code null} or less than {@code minValue}.
     */
    public static void atLeast(Object parent, String fieldName, Long field, long minValue) {
        nonNull(parent, fieldName, field);
        if (field < minValue) {
            throw new IllegalArgumentException(
                    String.format("%s cannot be less than %d: %s", fieldName, minValue, parent));
        }
    }

    /**
     * Throws an exception if {@code field} is {@code null} or greater than {@code maxValue}.
     */
    public static void atMost(Object parent, String fieldName, Integer field, int maxValue) {
        atMost(parent, fieldName, Long.valueOf(field), maxValue);
    }

    /**
     * Throws an exception if {@code field} is {@code null} or greater than {@code maxValue}.
     */
    public static void atMost(Object parent, String fieldName, Long field, long maxValue) {
        nonNull(parent, fieldName, field);
        if (field > maxValue) {
            throw new IllegalArgumentException(
                    String.format("%s cannot be greater than %d: %s", fieldName, maxValue, parent));
        }
    }

    // Object checks

    /**
     * Throws an exception if {@code field} is {@code null}.
     */
    public static void nonNull(Object parent, String fieldName, Object field) {
        if (field == null) {
            throw new IllegalArgumentException(String.format("%s cannot be null: %s", fieldName, parent));
        }
    }

    /**
     * Throws an exception if {@code field} is not {@code null}.
     */
    public static void isNull(Object parent, String fieldName, Object field) {
        if (field != null) {
            throw new IllegalArgumentException(String.format("%s must be null: %s", fieldName, parent));
        }
    }
}
