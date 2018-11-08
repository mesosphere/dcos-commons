package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.auth.CachedTokenProvider;
import com.mesosphere.sdk.dcos.auth.TokenProvider;
import com.mesosphere.sdk.dcos.clients.ServiceAccountIAMTokenClient;
import com.mesosphere.sdk.framework.EnvStore;
import com.mesosphere.sdk.generated.SDKBuildInfo;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.state.GoalStateOverride;

import com.auth0.jwt.algorithms.Algorithm;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos.Credential;
import org.bouncycastle.util.io.pem.PemReader;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class encapsulates global Scheduler settings retrieved from the environment. Presented as a non-static object
 * to simplify scheduler tests, and to make it painfully obvious when global settings are being used in awkward places.
 */
@SuppressWarnings("checkstyle:MethodCount")
public final class SchedulerConfig {

  /**
   * (Multi-service only) Envvar to specify the number of services that may be reserving footprint at the same time.
   * <ul><li>High value (or <=0 for no limit): Faster deployment across multiple services, but risks deadlocks if two
   * simultaneous deployments  both want the same resources. However, this can be ameliorated by enforcing per-service
   * quotas.</li>
   * <li>Lower value: Slower deployment, but reduces the risk of two deploying services being stuck on the same
   * resource. Setting the value to {@code 1} should remove the risk entirely.</li></ul>
   */
  public static final String RESERVE_DISCIPLINE_ENV = "RESERVE_DISCIPLINE";

  public static final String MESOS_API_VERSION_V1 = "V1";

  private static final Logger LOGGER = LoggingUtils.getLogger(SchedulerConfig.class);

  /**
   * Envvar to specify a custom amount of time to wait for the Scheduler API to come up during startup.
   */
  private static final String API_SERVER_TIMEOUT_S_ENV = "API_SERVER_TIMEOUT_S";

  /**
   * The default number of seconds to wait for the Scheduler API to come up during startup.
   */
  private static final int DEFAULT_API_SERVER_TIMEOUT_S = 600;

  /**
   * (Multi-service only) Envvar to specify the amount of time in seconds for a removed service to complete uninstall
   * before removing it. If this envvar is negative or zero, then the timeout is disabled and the Scheduler will wait
   * indefinitely for removed services to complete. If the Scheduler itself is being uninstalled, it will always wait
   * indefinitely, regardless of this setting.
   */
  private static final String SERVICE_REMOVAL_TIMEOUT_S_ENV = "SERVICE_REMOVAL_TIMEOUT_S";

  /**
   * The default number of seconds to wait for a service to finish uninstall before being forcibly removed.
   */
  private static final int DEFAULT_SERVICE_REMOVE_TIMEOUT_S = 600;

  /**
   * The default reserve discipline, which is to have no limit on deployments. Operators may configure a limit on the
   * number of parallel deployments via the above envvar.
   */
  private static final int DEFAULT_RESERVE_DISCIPLINE = 0;

  /**
   * Envvar name to specify a custom amount of time before auth token expiration that will trigger auth
   * token refresh.
   */
  private static final String AUTH_TOKEN_REFRESH_THRESHOLD_S_ENV = "AUTH_TOKEN_REFRESH_THRESHOLD_S";

  /**
   * The default number of seconds to set a connection timeout when refreshing an auth token.
   */
  private static final int DEFAULT_AUTH_TOKEN_REFRESH_TIMEOUT_S = 30;

  /**
   * The default number of seconds before auth token expiration that will trigger auth token refresh.
   */
  private static final int DEFAULT_AUTH_TOKEN_REFRESH_THRESHOLD_S = 30;

  /**
   * Specifies the URI of the bootstrap artifact to be used when launching stopped tasks.
   */
  private static final String BOOTSTRAP_URI_ENV = "BOOTSTRAP_URI";

  /**
   * Specifies the URI of the libmesos package used by the scheduler itself.
   */
  private static final String LIBMESOS_URI_ENV = "LIBMESOS_URI";

  /**
   * Specifies the Java URI to be used when launching tasks.
   */
  private static final String JAVA_URI_ENV = "JAVA_URI";

  /**
   * Standard Java envvar pointing to the JRE location on disk.
   */
  private static final String JAVA_HOME_ENV = "JAVA_HOME";

  /**
   * When set, specifies that uninstall should be performed.
   */
  private static final String SDK_UNINSTALL = "SDK_UNINSTALL";

  /**
   * Controls whether ZK write-through caching is disabled (enabled by default).
   * If this envvar is set (to anything at all), the cache is disabled.
   */
  private static final String DISABLE_STATE_CACHE_ENV = "DISABLE_STATE_CACHE";

  /**
   * Controls whether deadlocks should lead to the scheduler process exiting (enabled by default).
   * If this envvar is set (to anything at all), the scheduler will not exit if a deadlock is encountered.
   */
  private static final String DISABLE_DEADLOCK_EXIT_ENV = "DISABLE_DEADLOCK_EXIT";

  /**
   * Controls whether the framework will request that offers be suppressed when the service(s) are idle (enabled by
   * default). If this envvar is set (to anything at all), then offer suppression is disabled.
   */
  private static final String DISABLE_SUPPRESS_ENV = "DISABLE_SUPPRESS";

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
  private static final String SIDECHANNEL_AUTH_ENV = "DCOS_SERVICE_ACCOUNT_CREDENTIAL";

  /**
   * Environment variables which advertise to the service what the DC/OS package name and package version are.
   */
  private static final String PACKAGE_NAME_ENV = "PACKAGE_NAME";

  private static final String PACKAGE_VERSION_ENV = "PACKAGE_VERSION";

  private static final String PACKAGE_BUILD_TIME_EPOCH_MS_ENV = "PACKAGE_BUILD_TIME_EPOCH_MS";

  /**
   * An environment variable that advertises to the service what region its tasks should run in.
   */
  private static final String SERVICE_REGION_ENV = "SERVICE_REGION";

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
   * Environment variable for manually configuring the command to run when pausing a pod.
   */
  private static final String PAUSE_OVERRIDE_CMD_ENV = "PAUSE_OVERRIDE_CMD";

  /**
   * Environment variables for configuring implicit reconciliation:
   * <ul><li>Delay before first implicit reconciliation is triggered (in milliseconds).</li>
   * <li>Duration between implicit reconciliations (in milliseconds).</li></ul>
   */
  private static final String IMPLICIT_RECONCILIATION_DELAY_MS_ENV =
      "IMPLICIT_RECONCILIATION_DELAY_MS";

  private static final String IMPLICIT_RECONCILIATION_PERIOD_MS_ENV =
      "IMPLICIT_RECONCILIATION_PERIOD_MS";

  /**
   * Environment variable for allowing region awareness.
   */
  private static final String ALLOW_REGION_AWARENESS_ENV = "ALLOW_REGION_AWARENESS";

  /**
   * Environment variable for setting a custom TLD for autoip endpoints.
   */
  private static final String SERVICE_TLD_ENV = "SERVICE_TLD";

  /**
   * Environment variable for setting a custom TLD for VIP endpoints.
   */
  private static final String VIP_TLD_ENV = "VIP_TLD";

  /**
   * Environment variable for setting a custom name for the Marathon instance running the service.
   */
  private static final String MARATHON_NAME_ENV = "MARATHON_NAME";

  /**
   * Environment variable for the IP address of the scheduler task. Note, this is dependent on the fact we are using
   * the command executor.
   */
  private static final String LIBPROCESS_IP_ENV = "LIBPROCESS_IP";

  /**
   * Environment variable to control use of legacy killUnneededTasks sematics. By default this is disabled.
   */
  private static final String USE_LEGACY_UNNEEDED_TASK_KILLS = "USE_LEGACY_KILL_UNNEEDED_TASKS";

  /**
   * We print the build info here because this is likely to be a very early point in the service's execution. In a
   * multi-service situation, however, this code may be getting invoked multiple times, so only print if we haven't
   * printed before.
   */
  private static final AtomicBoolean PRINTED_BUILD_INFO = new AtomicBoolean(false);

  private final EnvStore envStore;

  private SchedulerConfig(EnvStore envStore) {
    this.envStore = envStore;

    if (!PRINTED_BUILD_INFO.getAndSet(true)) {
      LOGGER.info("Build information:\n{} ", getBuildInfo().toString(2));
    }
  }

  /**
   * Returns a new {@link SchedulerConfig} instance which is based off the process environment.
   */
  public static SchedulerConfig fromEnv() {
    return fromEnvStore(EnvStore.fromEnv());
  }

  /**
   * Returns a new {@link SchedulerConfig} instance which is based off the provided env store.
   */
  public static SchedulerConfig fromEnvStore(EnvStore envStore) {
    return new SchedulerConfig(envStore);
  }

  /**
   * Returns the configured time to wait for the API server to come up during scheduler initialization.
   */
  public Duration getApiServerInitTimeout() {
    return Duration.ofSeconds(
        envStore.getOptionalInt(API_SERVER_TIMEOUT_S_ENV, DEFAULT_API_SERVER_TIMEOUT_S)
    );
  }

  /**
   * Returns the configured time to wait for a service to be removed in a multi-service scheduler.
   */
  public Duration getMultiServiceRemovalTimeout() {
    return Duration.ofSeconds(
        envStore.getOptionalInt(SERVICE_REMOVAL_TIMEOUT_S_ENV, DEFAULT_SERVICE_REMOVE_TIMEOUT_S));
  }

  /**
   * Returns the number of services that can be simultaneously reserving in a multi-service scheduler, or {@code <=0}
   * for no limit.
   */
  public int getMultiServiceReserveDiscipline() {
    return envStore.getOptionalInt(RESERVE_DISCIPLINE_ENV, DEFAULT_RESERVE_DISCIPLINE);
  }

  /**
   * Returns the configured API port, or throws {@link EnvStore.ConfigException} if the environment lacked the
   * required information.
   */
  public int getApiServerPort() {
    return envStore.getRequiredInt(MARATHON_API_PORT_ENV);
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

  @SuppressWarnings("checkstyle:MultipleStringLiterals")
  public String getDcosSpace() {
    // Try in order: DCOS_SPACE, MARATHON_APP_ID, "/"
    String value = envStore.getOptional(DCOS_SPACE_ENV, null);
    if (value != null) {
      return value;
    }
    return envStore.getOptional(MARATHON_APP_ID_ENV, "/");
  }

  public Optional<String> getSchedulerRegion() {
    return Optional.ofNullable(envStore.getOptional(SERVICE_REGION_ENV, null));
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

  public boolean isDeadlockExitEnabled() {
    return !envStore.isPresent(DISABLE_DEADLOCK_EXIT_ENV);
  }

  public boolean isSuppressEnabled() {
    return !envStore.isPresent(DISABLE_SUPPRESS_ENV);
  }

  public boolean isUninstallEnabled() {
    return envStore.isPresent(SDK_UNINSTALL);
  }

  public boolean useLegacyUnneededTaskKills() {
    return envStore.isPresent(USE_LEGACY_UNNEEDED_TASK_KILLS);
  }

  /**
   * Returns whether it appears that side channel auth should be used when creating the SchedulerDriver. Side channel
   * auth may take the form of Bouncer-based framework authentication or potentially other methods in the future (e.g.
   * Kerberos).
   */
  public boolean isSideChannelActive() {
    return envStore.isPresent(SIDECHANNEL_AUTH_ENV);
  }

  /**
   * Returns a token provider which may be used to retrieve DC/OS JWT auth tokens, or throws an
   * exception if the local environment doesn't provide the needed information (e.g. on a DC/OS
   * Open cluster)
   */
  @SuppressWarnings("checkstyle:MultipleStringLiterals")
  public TokenProvider getDcosAuthTokenProvider() throws IOException {
    JSONObject serviceAccountObject = loadFileOrEnvSecret();
    try (PemReader pemReader = new PemReader(
        new StringReader(serviceAccountObject.getString("private_key"))
    ))
    {
      RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory
          .getInstance("RSA")
          .generatePrivate(new PKCS8EncodedKeySpec(pemReader.readPemObject().getContent()));

      RSAPublicKey publicKey = (RSAPublicKey) KeyFactory
          .getInstance("RSA")
          .generatePublic(
              new RSAPublicKeySpec(
                  privateKey.getModulus(),
                  privateKey.getPrivateExponent()
              )
          );

      ServiceAccountIAMTokenClient serviceAccountIAMTokenProvider =
          new ServiceAccountIAMTokenClient(
              new DcosHttpExecutor(
                  new DcosHttpClientBuilder()
                      .setDefaultConnectionTimeout(DEFAULT_AUTH_TOKEN_REFRESH_TIMEOUT_S)
                      .setRedirectStrategy(new LaxRedirectStrategy())
              ),
              serviceAccountObject.getString("uid"),
              Algorithm.RSA256(publicKey, privateKey)
          );

      Duration authTokenRefreshThreshold = Duration.ofSeconds(envStore.getOptionalInt(
          AUTH_TOKEN_REFRESH_THRESHOLD_S_ENV, DEFAULT_AUTH_TOKEN_REFRESH_THRESHOLD_S));

      return new CachedTokenProvider(
          serviceAccountIAMTokenProvider,
          authTokenRefreshThreshold,
          this
      );
    } catch (InvalidKeySpecException e) {
      throw new IllegalArgumentException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private JSONObject loadFileOrEnvSecret() throws IOException {
    String content = envStore.getRequired(SIDECHANNEL_AUTH_ENV);
    JSONObject serviceAccountObject;
    if (Files.isRegularFile(Paths.get(content))) {
      LOGGER.info("Reading file {} to load secrets", content);
      serviceAccountObject = new JSONObject(IOUtils.toString(new FileInputStream(content)));
    } else {
      LOGGER.info("Reading service account information from {}", SIDECHANNEL_AUTH_ENV);
      serviceAccountObject = new JSONObject(content);
    }
    return serviceAccountObject;
  }

  /**
   * Returns the package name as advertised in the scheduler environment.
   */
  private String getPackageName() {
    return envStore.getRequired(PACKAGE_NAME_ENV);
  }

  /**
   * Returns the package version as advertised in the scheduler environment.
   */
  private String getPackageVersion() {
    return envStore.getRequired(PACKAGE_VERSION_ENV);
  }

  /**
   * Returns the package build time (unix epoch milliseconds) as advertised in the scheduler environment.
   */
  private long getPackageBuildTimeMs() {
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
    return envStore.getOptional(MESOS_API_VERSION_ENV, MESOS_API_VERSION_V1);
  }

  /**
   * Returns the command to be run when pausing a Task.
   */
  public String getPauseOverrideCmd() {
    return envStore.getOptional(PAUSE_OVERRIDE_CMD_ENV, GoalStateOverride.PAUSE_COMMAND);
  }

  /**
   * Returns the TLD to be used in advertised autoip endpoints. This may be overridden in cases where autoip support
   * isn't available, or if some other TLD should be used instead.
   * <p>
   * Resolves to the IP of the host iff the container if on the host network and the IP of the container iff the
   * container is on the overlay network. If the container is on multiple virtual networks or experimenting with
   * different DNS providers this TLD may have unexpected behavior.
   */
  public String getAutoipTLD() {
    return envStore.getOptional(SERVICE_TLD_ENV, "autoip.dcos.thisdcos.directory");
  }

  /**
   * Returns the TLD to be used in advertised VIP endpoints. This may be overridden in cases where VIPs aren't
   * available, or if some other TLD should be used instead.
   */
  public String getVipTLD() {
    return envStore.getOptional(VIP_TLD_ENV, "l4lb.thisdcos.directory");
  }

  /**
   * Returns the name of the Marathon instance managing the service. This may be overridden in cases where a
   * non-default Marathon instance (e.g. MoM) is running the scheduler.
   * <p>
   * This is used for constructing an endpoint for reaching the Scheduler from tasks, specifically for config template
   * distribution.
   */
  public String getMarathonName() {
    return envStore.getOptional(MARATHON_NAME_ENV, "marathon");
  }

  /**
   * Returns the duration to wait after framework registration before performing the first implicit reconcilation, in
   * milliseconds.
   */
  public long getImplicitReconcileDelayMs() {
    return envStore.getOptionalLong(IMPLICIT_RECONCILIATION_DELAY_MS_ENV, 0 /* no delay */);
  }

  /**
   * Returns the duration to wait between implicit reconcilations, in milliseconds.
   */
  public long getImplicitReconcilePeriodMs() {
    return envStore.getOptionalLong(
        IMPLICIT_RECONCILIATION_PERIOD_MS_ENV,
        TimeUnit.SECONDS.convert(1L, TimeUnit.HOURS)
    );
  }

  /**
   * Returns whether region awareness should be enabled. In 1.11, this is an explicit opt-in by users.
   */
  public boolean isRegionAwarenessEnabled() {
    return envStore.getOptionalBoolean(ALLOW_REGION_AWARENESS_ENV, false);
  }

  /**
   * Returns the IP of the scheduler task's container. Note, this is dependent on the fact we are using the command
   * executor.
   */
  public String getSchedulerIP() {
    return envStore.getRequired(LIBPROCESS_IP_ENV);
  }

  public JSONObject getBuildInfo() {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(PACKAGE_NAME_ENV, getPackageName());
    jsonObject.put(PACKAGE_VERSION_ENV, getPackageVersion());
    jsonObject.put("PACKAGE_BUILT_AT", Instant.ofEpochMilli(getPackageBuildTimeMs()));
    jsonObject.put("SDK_NAME", SDKBuildInfo.NAME);
    jsonObject.put("SDK_VERSION", SDKBuildInfo.VERSION);
    jsonObject.put("SDK_GIT_SHA", SDKBuildInfo.GIT_SHA);
    jsonObject.put("SDK_BUILT_AT", Instant.ofEpochMilli(SDKBuildInfo.BUILD_TIME_EPOCH_MS));
    return jsonObject;
  }
}
