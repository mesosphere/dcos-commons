package org.apache.mesos.scheduler;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.Protos.Credential;
import org.apache.mesos.Protos.FrameworkInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.protobuf.ByteString;

/**
 * Factory class for creating {@link MesosSchedulerDriver}s.
 */
public class SchedulerDriverFactory {

  private static final Logger LOGGER =
    LoggerFactory.getLogger(SchedulerDriverFactory.class);

  /**
   * If this environment variable is present in the scheduler environment, the master is using
   * some form of sidechannel auth. When this environment variable is present, we should always
   * provide a {@link Credential} with (only) the principal set.
   */
  private static String SIDECHANNEL_AUTH_MAGIC_ENVNAME = "DCOS_SERVICE_ACCOUNT_CREDENTIAL";

  /**
   * Creates and returns a new {@link SchedulerDriver} without a credential secret.
   *
   * @param scheduler The Framework {@link Scheduler} implementation which should receive callbacks
   *     from the {@link SchedulerDriver}
   * @param frameworkInfo The {@link FrameworkInfo} which describes the framework implementation.
   *     The 'principal' field MUST be populated and non-empty
   * @param masterZkUrl The URL of the currently active Mesos Master, of the form "zk://host/mesos"
   * @return A {@link SchedulerDriver} configured with the provided info
   * @throws IllegalArgumentException if {@link FrameworkInfo}.principal is unset or empty when
   *     authentication is needed
   */
  public SchedulerDriver create(
      final Scheduler scheduler, final FrameworkInfo frameworkInfo, final String masterZkUrl) {
    return create(scheduler, frameworkInfo, masterZkUrl, null /* credentialSecret */);
  }

  /**
   * Creates and returns a new {@link SchedulerDriver} with the provided credential secret.
   *
   * @param scheduler The Framework {@link Scheduler} implementation which should receive callbacks
   *     from the {@link SchedulerDriver}
   * @param frameworkInfo The {@link FrameworkInfo} which describes the framework implementation.
   *     The 'principal' field MUST be populated and non-empty
   * @param masterZkUrl The URL of the currently active Mesos Master, of the form "zk://host/mesos"
   * @param credentialSecret The secret to be included in the framework
   *     {@link org.apache.mesos.Protos.Credential}, ignored if {@code null}/empty
   * @return A {@link SchedulerDriver} configured with the provided info
   * @throws IllegalArgumentException if {@link FrameworkInfo}.principal is unset or empty when
   *     authentication is needed
   */
  public SchedulerDriver create(
      final Scheduler scheduler,
      final FrameworkInfo frameworkInfo,
      final String masterZkUrl,
      final byte[] credentialSecret) {

    Credential credential;
    if (credentialSecret != null && credentialSecret.length > 0) {
      // User has manually provided a Secret. Provide a Credential with Principal+Secret.
      if (!frameworkInfo.hasPrincipal() || StringUtils.isEmpty(frameworkInfo.getPrincipal())) {
        throw new IllegalArgumentException(
            "Unable to create MesosSchedulerDriver for secret auth, "
                + "FrameworkInfo lacks required principal: " + frameworkInfo.toString());
      }
      credential = Credential.newBuilder()
          .setPrincipal(frameworkInfo.getPrincipal())
          .setSecretBytes(ByteString.copyFrom(credentialSecret))
          .build();
      LOGGER.info("Creating secret authenticated MesosSchedulerDriver for "
          + "scheduler = {}, frameworkInfo = {}, masterZkUrl = {}, credentialSecret = {}",
          scheduler, frameworkInfo, masterZkUrl, credentialSecret);
    } else if (isSideChannelActive()) {
      // Sidechannel auth is enabled. Provide a Credential with only the Principal set.
      if (!frameworkInfo.hasPrincipal() || StringUtils.isEmpty(frameworkInfo.getPrincipal())) {
        throw new IllegalArgumentException(
            "Unable to create MesosSchedulerDriver for sidechannel auth, "
                + "FrameworkInfo lacks required principal: " + frameworkInfo.toString());
      }
      credential = Credential.newBuilder()
          .setPrincipal(frameworkInfo.getPrincipal())
          .build();
      LOGGER.info("Creating sidechannel authenticated MesosSchedulerDriver for "
          + "scheduler = {}, frameworkInfo = {}, masterZkUrl = {}",
          scheduler, frameworkInfo, masterZkUrl);
    } else {
      // No auth. Provide no credential.
      credential = null;
      LOGGER.info("Creating unauthenticated MesosSchedulerDriver for "
          + "scheduler = {}, frameworkInfo = {}, masterZkUrl = {}",
          scheduler, frameworkInfo, masterZkUrl);
    }

    return createInternal(scheduler, frameworkInfo, masterZkUrl, credential);
  }

  /**
   * Broken out into a separate function to allow testing with custom SchedulerDrivers.
   */
  protected SchedulerDriver createInternal(
      final Scheduler scheduler,
      final FrameworkInfo frameworkInfo,
      final String masterZkUrl,
      final Credential credential) {
    if (credential == null) {
      return new MesosSchedulerDriver(scheduler, frameworkInfo, masterZkUrl);
    } else {
      return new MesosSchedulerDriver(scheduler, frameworkInfo, masterZkUrl, credential);
    }
  }

  /**
   * Returns whether it appears that sidechannel auth should be used when creating the
   * SchedulerDriver. Broken out into a separate function to customize behavior in tests.
   */
  protected boolean isSideChannelActive() {
    return System.getenv(SIDECHANNEL_AUTH_MAGIC_ENVNAME) != null;
  }
}
