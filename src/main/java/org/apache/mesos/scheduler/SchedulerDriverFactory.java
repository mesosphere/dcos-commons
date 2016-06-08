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
   * Creates and returns a new {@link SchedulerDriver} without a credential secret.
   *
   * @param scheduler The Framework {@link Scheduler} implementation which should receive callbacks
   * from the {@link SchedulerDriver}
   * @param frameworkInfo The {@link FrameworkInfo} which describes the framework implementation.
   * The 'principal' field MUST be populated and non-empty
   * @param masterZkUrl The URL of the currently active Mesos Master, of the form "zk://host/mesos"
   * @return A {@link SchedulerDriver} configured with the provided info
   * @throws IllegalArgumentException if {@link FrameworkInfo}.principal is unset or empty
   */
  public SchedulerDriver create(
      final Scheduler scheduler, final FrameworkInfo frameworkInfo, final String masterZkUrl) {
    return create(scheduler, frameworkInfo, masterZkUrl, null /* credentialSecret */);
  }

  /**
   * Creates and returns a new {@link SchedulerDriver} with the provided credential secret.
   *
   * @param scheduler The Framework {@link Scheduler} implementation which should receive callbacks
   * from the {@link SchedulerDriver}
   * @param frameworkInfo The {@link FrameworkInfo} which describes the framework implementation.
   * The 'principal' field MUST be populated and non-empty
   * @param masterZkUrl The URL of the currently active Mesos Master, of the form "zk://host/mesos"
   * @param credentialSecret The secret to be included in the framework
   *     {@link org.apache.mesos.Protos.Credential}, ignored if {@code null}/empty
   * @return A {@link SchedulerDriver} configured with the provided info
   * @throws IllegalArgumentException if {@link FrameworkInfo}.principal is unset or empty
   */
  public SchedulerDriver create(
      final Scheduler scheduler,
      final FrameworkInfo frameworkInfo,
      final String masterZkUrl,
      final byte[] credentialSecret) {
    LOGGER.info("Creating MesosSchedulerDriver for "
        + "scheduler = {}, frameworkInfo = {}, masterZkUrl = {}, credentialSecret = {}",
        scheduler, frameworkInfo, masterZkUrl, credentialSecret);

    if (!frameworkInfo.hasPrincipal() || StringUtils.isEmpty(frameworkInfo.getPrincipal())) {
      throw new IllegalArgumentException(
          "Unable to create MesosSchedulerDriver, FrameworkInfo lacks a principal: "
              + frameworkInfo.toString());
    }

    Credential.Builder credentialBuilder = Credential.newBuilder()
        .setPrincipal(frameworkInfo.getPrincipal());
    if (credentialSecret != null && credentialSecret.length != 0) {
      credentialBuilder.setSecretBytes(ByteString.copyFrom(credentialSecret));
    }

    return createInternal(scheduler, frameworkInfo, masterZkUrl, credentialBuilder.build());
  }

  /**
   * Broken out into a separate function to allow testing with custom SchedulerDrivers.
   */
  protected SchedulerDriver createInternal(
      final Scheduler scheduler,
      final FrameworkInfo frameworkInfo,
      final String masterZkUrl,
      final Credential credential) {
    return new MesosSchedulerDriver(scheduler, frameworkInfo, masterZkUrl, credential);
  }
}
