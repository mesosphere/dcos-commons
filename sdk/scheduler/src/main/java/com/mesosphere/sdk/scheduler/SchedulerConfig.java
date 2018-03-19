package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.state.GoalStateOverride;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos.Credential;
import org.bouncycastle.util.io.pem.PemReader;
import org.json.JSONObject;

import com.auth0.jwt.algorithms.Algorithm;
import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.auth.CachedTokenProvider;
import com.mesosphere.sdk.dcos.auth.TokenProvider;
import com.mesosphere.sdk.dcos.clients.ServiceAccountIAMTokenClient;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Map;

/**
 * This class encapsulates global Scheduler settings retrieved from the environment. Presented as a non-static object
 * to simplify scheduler tests, and to make it painfully obvious when global settings are being used in awkward places.
 */
public class SchedulerConfig {

    /**
     * Exception which is thrown when failing to retrieve or parse a given flag value.
     */
    public static class ConfigException extends RuntimeException {

        /**
         * A machine-accessible error type.
         */
        public enum Type {
            UNKNOWN,
            NOT_FOUND,
            INVALID_VALUE
        }

        public static ConfigException notFound(String message) {
            return new ConfigException(Type.NOT_FOUND, message);
        }

        public static ConfigException invalidValue(String message) {
            return new ConfigException(Type.INVALID_VALUE, message);
        }

        private final Type type;

        private ConfigException(Type type, String message) {
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

    /**
     * Envvar name to specify a custom amount of time before auth token expiration that will trigger auth
     * token refresh.
     */
    private static final String AUTH_TOKEN_REFRESH_THRESHOLD_S_ENV = "AUTH_TOKEN_REFRESH_THRESHOLD_S";
    /** The default number of seconds to set a connection timeout when refreshing an auth token. */
    private static final int DEFAULT_AUTH_TOKEN_REFRESH_TIMEOUT_S = 30;
    /** The default number of seconds before auth token expiration that will trigger auth token refresh. */
    private static final int DEFAULT_AUTH_TOKEN_REFRESH_THRESHOLD_S = 30;

    /** Specifies the URI of the executor artifact to be used when launching tasks. */
    private static final String EXECUTOR_URI_ENV = "EXECUTOR_URI";
    /** Specifies the URI of the bootstrap artifact to be used when launching stopped tasks. */
    private static final String BOOTSTRAP_URI_ENV = "BOOTSTRAP_URI";
    /** Specifies the URI of the libmesos package used by the scheduler itself. */
    private static final String LIBMESOS_URI_ENV = "LIBMESOS_URI";
    /** Specifies the Java URI to be used when launching tasks. */
    private static final String JAVA_URI_ENV = "JAVA_URI";
    /** Standard Java envvar pointing to the JRE location on disk. */
    private static final String JAVA_HOME_ENV = "JAVA_HOME";
    /** When set, specifies that uninstall should be performed. */
    private static final String SDK_UNINSTALL = "SDK_UNINSTALL";

    /**
     * Controls whether ZK write-through caching is disabled (enabled by default).
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
     * Name of the scheduler in Marathon, e.g. "/path/to/myservice".
     */
    private static final String MARATHON_APP_ID_ENV = "MARATHON_APP_ID";

    /**
     * DC/OS Space to be used by this service, overriding the marathon app id.
     */
    private static final String DCOS_SPACE_ENV = "DCOS_SPACE";

    /**
     * If this environment variable is present in the scheduler environment, the master is using
     * some form of sidechannel auth. When this environment variable is present, we should always
     * provide a {@link Credential} with (only) the principal set.
     */
    private static final String SIDECHANNEL_AUTH_ENV_NAME = "DCOS_SERVICE_ACCOUNT_CREDENTIAL";

    /**
     * Environment variables which advertise to the service what the DC/OS package name and package version are.
     */
    private static final String PACKAGE_NAME_ENV = "PACKAGE_NAME";
    private static final String PACKAGE_VERSION_ENV = "PACKAGE_VERSION";
    private static final String PACKAGE_BUILD_TIME_EPOCH_MS_ENV = "PACKAGE_BUILD_TIME_EPOCH_MS";

    /**
     * Environment variables for configuring metrics reporting behavior.
     */
    private static final String STATSD_POLL_INTERVAL_S_ENV = "STATSD_POLL_INTERVAL_S";
    private static final String STATSD_UDP_HOST_ENV = "STATSD_UDP_HOST";
    private static final String STATSD_UDP_PORT_ENV = "STATSD_UDP_PORT";

    /**
     * Environment variables for configuring Mesos API version.
     */
    private static final String MESOS_API_VERSION_ENV = "MESOS_API_VERSION";

    /**
     * Environment variables for configuring goal state override behavior.
     */
    private static final String PAUSE_OVERRIDE_CMD_ENV = "PAUSE_OVERRIDE_CMD";

    /**
     * Environment variable for allowing region awareness.
     */
    private static final String ALLOW_REGION_AWARENESS_ENV = "ALLOW_REGION_AWARENESS";

    /**
     * Returns a new {@link SchedulerConfig} instance which is based off the process environment.
     */
    public static SchedulerConfig fromEnv() {
        return fromMap(System.getenv());
    }

    /**
     * Returns a new {@link SchedulerConfig} instance which is based off the provided custom environment map.
     */
    public static SchedulerConfig fromMap(Map<String, String> map) {
        return new SchedulerConfig(map);
    }

    private final EnvStore envStore;

    private SchedulerConfig(Map<String, String> flagMap) {
        this.envStore = new EnvStore(flagMap);
    }

    /**
     * Returns the configured time to wait for the API server to come up during scheduler initialization.
     */
    public Duration getApiServerInitTimeout() {
        return Duration.ofSeconds(envStore.getOptionalInt(API_SERVER_TIMEOUT_S_ENV, DEFAULT_API_SERVER_TIMEOUT_S));
    }

    /**
     * Returns the configured API port, or throws {@link ConfigException} if the environment lacked the required
     * information.
     */
    public int getApiServerPort() {
        return envStore.getRequiredInt(MARATHON_API_PORT_ENV);
    }

    public String getExecutorURI() {
        return envStore.getRequired(EXECUTOR_URI_ENV);
    }

    public String getBootstrapURI() {
        return envStore.getRequired(BOOTSTRAP_URI_ENV);
    }

    public String getLibmesosURI() {
        return envStore.getRequired(LIBMESOS_URI_ENV);
    }

    public String getJavaURI() {
        return envStore.getRequired(JAVA_URI_ENV);
    }

    public String getJavaHome() {
        return envStore.getRequired(JAVA_HOME_ENV);
    }

    public String getDcosSpace() {
        // Try in order: DCOS_SPACE, MARATHON_APP_ID, "/"
        String value = envStore.getOptional(DCOS_SPACE_ENV, null);
        if (value != null) {
            return value;
        }
        return envStore.getOptional(MARATHON_APP_ID_ENV, "/");
    }

    public String getSecretsNamespace(String serviceName) {
        String secretNamespace = getDcosSpace();
        if (secretNamespace.startsWith("/")) {
            secretNamespace = secretNamespace.substring(1);
        }

        return secretNamespace.isEmpty() ? serviceName : secretNamespace;
    }

    public boolean isStateCacheEnabled() {
        return !envStore.isPresent(DISABLE_STATE_CACHE_ENV);
    }

    public boolean isUninstallEnabled() {
        return envStore.isPresent(SDK_UNINSTALL);
    }

    /**
     * Returns whether it appears that side channel auth should be used when creating the SchedulerDriver. Side channel
     * auth may take the form of Bouncer-based framework authentication or potentially other methods in the future (e.g.
     * Kerberos).
     */
    public boolean isSideChannelActive() {
        return envStore.isPresent(SIDECHANNEL_AUTH_ENV_NAME);
    }

    /**
     * Returns a token provider which may be used to retrieve DC/OS JWT auth tokens, or throws an exception if the local
     * environment doesn't provide the needed information (e.g. on a DC/OS Open cluster)
     */
    public TokenProvider getDcosAuthTokenProvider() throws IOException {
        JSONObject serviceAccountObject = new JSONObject(envStore.getRequired(SIDECHANNEL_AUTH_ENV_NAME));
        PemReader pemReader = new PemReader(new StringReader(serviceAccountObject.getString("private_key")));
        try {
            RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(pemReader.readPemObject().getContent()));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPrivateExponent()));

            ServiceAccountIAMTokenClient serviceAccountIAMTokenProvider =
                    new ServiceAccountIAMTokenClient(
                            new DcosHttpExecutor(new DcosHttpClientBuilder()
                                    .setDefaultConnectionTimeout(DEFAULT_AUTH_TOKEN_REFRESH_TIMEOUT_S)
                                    .setRedirectStrategy(new LaxRedirectStrategy())),
                            serviceAccountObject.getString("uid"),
                            Algorithm.RSA256(publicKey, privateKey));

            Duration authTokenRefreshThreshold = Duration.ofSeconds(envStore.getOptionalInt(
                    AUTH_TOKEN_REFRESH_THRESHOLD_S_ENV, DEFAULT_AUTH_TOKEN_REFRESH_THRESHOLD_S));

            return new CachedTokenProvider(serviceAccountIAMTokenProvider, authTokenRefreshThreshold);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            pemReader.close();
        }
    }

    /**
     * Returns the package name as advertised in the scheduler environment.
     */
    public String getPackageName() {
        return envStore.getRequired(PACKAGE_NAME_ENV);
    }

    /**
     * Returns the package version as advertised in the scheduler environment.
     */
    public String getPackageVersion() {
        return envStore.getRequired(PACKAGE_VERSION_ENV);
    }

    /**
     * Returns the package build time (unix epoch milliseconds) as advertised in the scheduler environment.
     */
    public long getPackageBuildTimeMs() {
        return envStore.getRequiredLong(PACKAGE_BUILD_TIME_EPOCH_MS_ENV);
    }

    /**
     * Returns the interval in seconds between StatsD reports.
     */
    public long getStatsDPollIntervalS() {
        return envStore.getOptionalLong(STATSD_POLL_INTERVAL_S_ENV, 10);
    }

    /**
     * Returns the StatsD host.
     */
    public String getStatsdHost() {
        return envStore.getRequired(STATSD_UDP_HOST_ENV);
    }

    /**
     * Returns the StatsD port.
     */
    public int getStatsdPort() {
        return envStore.getRequiredInt(STATSD_UDP_PORT_ENV);
    }

    /**
     * Returns the Mesos API version.
     */
    public String getMesosApiVersion() {
        return envStore.getRequired(MESOS_API_VERSION_ENV);
    }

    /**
     * Returns the command to be run when pausing a Task.
     */
    public String getPauseOverrideCmd() {
        return envStore.getOptional(PAUSE_OVERRIDE_CMD_ENV, GoalStateOverride.PAUSE_COMMAND);
    }

    public boolean isregionAwarenessEnabled() {
        return Boolean.valueOf(envStore.getOptional(ALLOW_REGION_AWARENESS_ENV, "false"));
    }

    /**
     * Internal utility class for grabbing values from a mapping of flag values (typically the process env).
     */
    private static class EnvStore {

        private final Map<String, String> envMap;

        private EnvStore(Map<String, String> envMap) {
            this.envMap = envMap;
        }

        private int getOptionalInt(String envKey, int defaultValue) {
            return toInt(envKey, getOptional(envKey, String.valueOf(defaultValue)));
        }

        private long getOptionalLong(String envKey, long defaultValue) {
            return toLong(envKey, getOptional(envKey, String.valueOf(defaultValue)));
        }

        private int getRequiredInt(String envKey) {
            return toInt(envKey, getRequired(envKey));
        }

        private long getRequiredLong(String envKey) {
            return toLong(envKey, getRequired(envKey));
        }

        private String getOptional(String envKey, String defaultValue) {
            String value = envMap.get(envKey);
            return (value == null) ? defaultValue : value;
        }

        private String getRequired(String envKey) {
            String value = envMap.get(envKey);
            if (value == null) {
                throw ConfigException.notFound(String.format("Missing required environment variable: %s", envKey));
            }
            return value;
        }

        private boolean isPresent(String envKey) {
            return envMap.containsKey(envKey);
        }

        /**
         * If the value cannot be parsed as an int, this points to the source envKey, and ensures that
         * {@link SchedulerConfig} calls only throw {@link ConfigException}.
         */
        private static int toInt(String envKey, String envVal) {
            try {
                return Integer.parseInt(envVal);
            } catch (NumberFormatException e) {
                throw ConfigException.invalidValue(String.format(
                        "Failed to parse configured environment variable '%s' as an integer: %s", envKey, envVal));
            }
        }

        /**
         * If the value cannot be parsed as a long, this points to the source envKey, and ensures that
         * {@link SchedulerConfig} calls only throw {@link ConfigException}.
         */
        private static long toLong(String envKey, String envVal) {
            try {
                return Long.parseLong(envVal);
            } catch (NumberFormatException e) {
                throw ConfigException.invalidValue(String.format(
                        "Failed to parse configured environment variable '%s' as an integer: %s", envKey, envVal));
            }
        }
    }
}
