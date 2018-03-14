package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods around construction of loggers.
 */
public class LoggingUtils {

    private static boolean loggingNamesEnabled = true;

    private LoggingUtils() {
        // do not instantiate
    }

    /**
     * Creates a logger which is tagged with the provided class.
     *
     * @param clazz the class using this logger
     */
    public static Logger getLogger(Class<?> clazz) {
        // Note: This currently results in a name like "LoggingUtils" in logs.
        // Consider including an abbreviated package prefix, e.g. "c.m.s.o.LoggingUtils"?
        return LoggerFactory.getLogger(clazz.getSimpleName());
    }

    /**
     * Creates a logger which is tagged with the provided class and the provided custom label.
     *
     * @param clazz the class using this logger
     * @param name  an additional context label detailing e.g. the name of the service being managed
     */
    public static Logger getLogger(Class<?> clazz, String name) {
        if (StringUtils.isEmpty(name) || !loggingNamesEnabled) {
            return getLogger(clazz);
        } else {
            return LoggerFactory.getLogger(String.format("(%s) %s", name, clazz.getSimpleName()));
        }
    }

    /**
     * Disables logger names in output.
     *
     * @see #getLogger(Class, String)
     */
    public static void disableNames() {
        loggingNamesEnabled = false;
    }
}