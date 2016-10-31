package org.apache.mesos.util;

import org.apache.mesos.offer.TaskUtils;

import java.util.Map;

/**
 * Determines the location of JAVA_HOME for a given task.
 */
public class JavaHomeDetector {
    public static final String JAVA_HOME = "JAVA_HOME";
    public static final String DEFAULT_JAVA_HOME = "jre1.8.0_91";
    public static final String DEFAULT_JAVA_URI =
            "https://downloads.mesosphere.com/dcos-commons/artifacts/jre-8u91-linux-x64.tar.gz";

    /**
     * Checks whether a given environment map contains JAVA_HOME variable.
     *
     * @param environment The environment map to inspect for presence of JAVA_HOME
     * @return {@code true} if JAVA_HOME is present; {@code false} otherwise.
     */
    public static boolean envHasJavaHome(Map<String, String> environment) {
        return environment.containsKey(JAVA_HOME);
    }

    /**
     * Determines the location of JAVA_HOME from process' environment. If not JAVA_HOME variable is set,
     * the it returns the {@link JavaHomeDetector.DEFAULT_JAVA_HOME}.
     *
     * @return String representing the JAVA_HOME
     */
    public static String getJavaHomeFromEnv() {
        return getJavaHomeFromEnv(System.getenv());
    }

    /**
     * Similar to {@code getJavaHomeFromEnv}.
     *
     * @param environment The environment map to inspect for presence of JAVA_HOME
     * @return String representing the JAVA_HOME
     */
    public static String getJavaHomeFromEnv(Map<String, String> environment) {
        return envHasJavaHome(environment) ? environment.get(JAVA_HOME) : DEFAULT_JAVA_HOME;
    }
}
