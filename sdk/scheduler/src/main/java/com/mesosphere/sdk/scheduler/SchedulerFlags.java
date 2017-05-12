package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.mesos.Protos.Credential;

import java.time.Duration;
import java.util.Map;

/**
 * This class encapsulates global Scheduler settings retrieved from the environment. Presented as a non-static object
 * to simplify scheduler tests, and to make it painfully obvious when global settings are being used in awkward places.
 */
public class SchedulerFlags {

    /**
     * Exception which is thrown when failing to retrieve or parse a given flag value.
     */
    public static class FlagException extends RuntimeException {

        /**
         * A machine-accessible error type.
         */
        public enum Type {
            UNKNOWN,
            NOT_FOUND,
            INVALID_VALUE
        }

        public static FlagException notFound(String message) {
            return new FlagException(Type.NOT_FOUND, message);
        }

        public static FlagException invalidValue(String message) {
            return new FlagException(Type.INVALID_VALUE, message);
        }

        private final Type type;

        private FlagException(Type type, String message) {
            super(message);
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return String.format("%s (errtype: %s)", super.getMessage(), type);
        }
    }

    /** Envvar to specify a custom amount of time to wait for the Scheduler API to come up during startup. */
    private static final String API_SERVER_TIMEOUT_S_ENV = "API_SERVER_TIMEOUT_S";
    /** The default number of seconds to wait for the Scheduler API to come up during startup. */
    private static final int DEFAULT_API_SERVER_TIMEOUT_S = 600;

    /** Specifies the URI of the executor artifact to be used when launching tasks. */
    private static final String EXECUTOR_URI_ENV = "EXECUTOR_URI";
    /** Specifies the URI of the libmesos package used by the scheduler itself. */
    private static final String LIBMESOS_URI_ENV = "LIBMESOS_URI";
    /** Specifies the Java URI to be used when launching tasks. */
    private static final String JAVA_URI_ENV = "JAVA_URI";
    /** Standard Java envvar pointing to the JRE location on disk. */
    private static final String JAVA_HOME_ENV = "JAVA_HOME";
    /** When set, specifies that uninstall should be performed. */
    private static final String SDK_UNINSTALL = "SDK_UNINSTALL";

    /**
     * Controls whether the {@link StateStoreCache} is disabled (enabled by default).
     * If this envvar is set (to anything at all), the cache is disabled.
     */
    private static final String DISABLE_STATE_CACHE_ENV = "DISABLE_STATE_CACHE";

    /**
     * When a port named {@code api} is added to the Marathon app definition for the scheduler, marathon should create
     * an envvar with this name in the scheduler env. This is preferred over using e.g. the {@code PORT0} envvar which
     * is against the index of the port in the list.
     */
    private static final String MARATHON_API_PORT_ENV = "PORT_API";

    /**
     * If this environment variable is present in the scheduler environment, the master is using
     * some form of sidechannel auth. When this environment variable is present, we should always
     * provide a {@link Credential} with (only) the principal set.
     */
    private static final String SIDECHANNEL_AUTH_ENV_NAME = "DCOS_SERVICE_ACCOUNT_CREDENTIAL";

    /**
     * Returns a new {@link SchedulerFlags} instance which is based off the process environment.
     */
    public static SchedulerFlags fromEnv() {
        return fromMap(System.getenv());
    }

    /**
     * Returns a new {@link SchedulerFlags} instance which is based off the provided custom environment map.
     */
    public static SchedulerFlags fromMap(Map<String, String> map) {
        return new SchedulerFlags(map);
    }

    private final FlagStore flagStore;

    private SchedulerFlags(Map<String, String> flagMap) {
        this.flagStore = new FlagStore(flagMap);
    }

    /**
     * Returns the configured time to wait for the API server to come up during scheduler initialization.
     */
    public Duration getApiServerInitTimeout() {
        return Duration.ofSeconds(flagStore.getOptionalInt(API_SERVER_TIMEOUT_S_ENV, DEFAULT_API_SERVER_TIMEOUT_S));
    }

    /**
     * Returns the configured API port, or throws {@link FlagException} if the environment lacked the required
     * information.
     */
    public int getApiServerPort() {
        return flagStore.getRequiredInt(MARATHON_API_PORT_ENV);
    }

    public String getExecutorURI() {
        return flagStore.getRequired(EXECUTOR_URI_ENV);
    }

    public String getLibmesosURI() {
        return flagStore.getRequired(LIBMESOS_URI_ENV);
    }

    public String getJavaURI() {
        return flagStore.getRequired(JAVA_URI_ENV);
    }

    public String getJavaHome() {
        return flagStore.getRequired(JAVA_HOME_ENV);
    }

    public boolean isStateCacheEnabled() {
        return !flagStore.isPresent(DISABLE_STATE_CACHE_ENV);
    }

    public boolean isUninstallEnabled() {
        return flagStore.isPresent(SDK_UNINSTALL);
    }

    /**
     * Returns whether it appears that side channel auth should be used when creating the SchedulerDriver. Side channel
     * auth may take the form of Bouncer-based framework authentication or potentially other methods in the future (e.g.
     * Kerberos).
     */
    public boolean isSideChannelActive() {
        return flagStore.isPresent(SIDECHANNEL_AUTH_ENV_NAME);
    }

    /**
     * Internal utility class for grabbing values from a mapping of flag values (typically the process env).
     */
    private static class FlagStore {

        private final Map<String, String> flagMap;

        private FlagStore(Map<String, String> flagMap) {
            this.flagMap = flagMap;
        }

        private int getOptionalInt(String envKey, int defaultValue) {
            return toInt(envKey, getOptional(envKey, String.valueOf(defaultValue)));
        }

        private int getRequiredInt(String envKey) {
            return toInt(envKey, getRequired(envKey));
        }

        private String getOptional(String envKey, String defaultValue) {
            String value = flagMap.get(envKey);
            return (value == null) ? defaultValue : value;
        }

        private String getRequired(String envKey) {
            String value = flagMap.get(envKey);
            if (value == null) {
                throw FlagException.notFound(String.format("Missing required environment variable: %s", envKey));
            }
            return value;
        }

        private boolean isPresent(String envKey) {
            return flagMap.containsKey(envKey);
        }

        /**
         * If the value cannot be parsed as an int, this points to the source envKey, and ensures that
         * {@link SchedulerFlags} calls only throw {@link FlagException}.
         */
        private static int toInt(String envKey, String envVal) {
            try {
                return Integer.parseInt(envVal);
            } catch (NumberFormatException e) {
                throw FlagException.invalidValue(String.format(
                        "Failed to parse configured environment variable '%s' as an integer: %s", envKey, envVal));
            }
        }
    }
}
