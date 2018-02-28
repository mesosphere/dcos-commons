package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods around construction of loggers.
 */
public class LoggingUtils {
    private LoggingUtils() {
        // do not instantiate
    }

    /**
     * Creates a logger which is tagged with the provided class and the provided custom label.
     */
    public static Logger getLogger(Class<?> clazz, String name) {
        if (StringUtils.isEmpty(name)) {
            return LoggerFactory.getLogger(clazz);
        } else {
            return LoggerFactory.getLogger(String.format("%s(%s)", clazz.getName(), name));
        }
    }
}
