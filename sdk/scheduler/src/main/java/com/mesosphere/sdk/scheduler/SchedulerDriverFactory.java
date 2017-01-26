package com.mesosphere.sdk.scheduler;

import com.google.protobuf.ByteString;
import com.mesosphere.mesos.HTTPAdapter.MesosToSchedulerDriverAdapter;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.Credential;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String SIDECHANNEL_AUTH_ENV_NAME = "DCOS_SERVICE_ACCOUNT_CREDENTIAL";

    /**
     * Creates and returns a new {@link SchedulerDriver} without a credential secret.
     *
     * @param scheduler The Framework {@link Scheduler} implementation which should receive callbacks
     *     from the {@link SchedulerDriver}
     * @param frameworkInfo The {@link FrameworkInfo} which describes the framework implementation.
     *     The 'principal' field MUST be populated and non-empty
     * @param masterUrl The URL of the currently active Mesos Master, of the form "zk://host/mesos"
     * @return A {@link SchedulerDriver} configured with the provided info
     * @throws IllegalArgumentException if {@link FrameworkInfo}.principal is unset or empty when
     *     authentication is needed
     */
    public SchedulerDriver create(
            final Scheduler scheduler, final FrameworkInfo frameworkInfo, final String masterUrl) {
        return create(scheduler, frameworkInfo, masterUrl, null /* credentialSecret */);
    }

    /**
     * Creates and returns a new {@link SchedulerDriver} with the provided credential secret.
     *
     * @param scheduler The Framework {@link Scheduler} implementation which should receive callbacks
     *     from the {@link SchedulerDriver}
     * @param frameworkInfo The {@link FrameworkInfo} which describes the framework implementation.
     *     The 'principal' field MUST be populated and non-empty
     * @param masterUrl The URL of the currently active Mesos Master, of the form "zk://host/mesos"
     * @param credentialSecret The secret to be included in the framework
     *     {@link org.apache.mesos.Protos.Credential}, ignored if {@code null}/empty
     * @return A {@link SchedulerDriver} configured with the provided info
     * @throws IllegalArgumentException if {@link FrameworkInfo}.principal is unset or empty when
     *     authentication is needed
     */
    public SchedulerDriver create(
            final Scheduler scheduler,
            final FrameworkInfo frameworkInfo,
            final String masterUrl,
            final byte[] credentialSecret) {
        Credential credential;
        if (credentialSecret != null && credentialSecret.length > 0) {
            // User has manually provided a Secret. Provide a Credential with Principal + Secret.
            // (note: we intentionally avoid logging the content of the credential secret, just in case)
            LOGGER.info("Creating secret authenticated MesosSchedulerDriver for "
                    + "scheduler[{}], frameworkInfo[{}], masterUrl[{}], credentialSecret[{} bytes]",
                    scheduler, frameworkInfo, masterUrl, credentialSecret.length);
            credential = Credential.newBuilder()
                    .setPrincipal(getPrincipal(frameworkInfo, "secret"))
                    .setSecretBytes(ByteString.copyFrom(credentialSecret))
                    .build();
        } else if (isSideChannelActive()) {
            // Sidechannel auth is enabled. Provide a Credential with only the Principal set.
            LOGGER.info("Creating sidechannel authenticated MesosSchedulerDriver for "
                    + "scheduler[{}], frameworkInfo[{}], masterUrl[{}]",
                    scheduler, frameworkInfo, masterUrl);
            credential = Credential.newBuilder()
                    .setPrincipal(getPrincipal(frameworkInfo, "sidechannel"))
                    .build();
        } else {
            // No auth. Provide no credential.
            LOGGER.info("Creating unauthenticated MesosSchedulerDriver for "
                    + "scheduler[{}], frameworkInfo[{}], masterUrl[{}]",
                    scheduler, frameworkInfo, masterUrl);
            credential = null;
        }
        return createInternal(scheduler, frameworkInfo, masterUrl, credential);
    }

    /**
     * Broken out into a separate function to allow testing with custom SchedulerDrivers.
     */
    protected SchedulerDriver createInternal(
            final Scheduler scheduler,
            final FrameworkInfo frameworkInfo,
            final String masterUrl,
            final Credential credential) {
        if (credential == null) {
            return new MesosToSchedulerDriverAdapter(scheduler, frameworkInfo, masterUrl, true);
        } else {
            return new MesosToSchedulerDriverAdapter(scheduler, frameworkInfo, masterUrl, true, credential);
        }
    }

    /**
     * Returns whether it appears that side channel auth should be used when creating the
     * SchedulerDriver. Side channel auth may take the form of Bouncer-based framework authentication
     * or potentially other methods in the future (eg Kerberos). Broken out into a separate function
     * to allow easy customization in tests.
     */
    protected boolean isSideChannelActive() {
        return System.getenv(SIDECHANNEL_AUTH_ENV_NAME) != null;
    }

    /**
     * Extracts the Principal name from the provided {@link FrameworkInfo}, or throws an
     * {@link IllegalArgumentException} (mentioning the provided {@code authType}) if the Principal
     * is unavailable.
     */
    private static String getPrincipal(final FrameworkInfo frameworkInfo, final String authType) {
        if (!frameworkInfo.hasPrincipal() || StringUtils.isEmpty(frameworkInfo.getPrincipal())) {
            throw new IllegalArgumentException(
                    "Unable to create MesosSchedulerDriver for " + authType + " auth, "
                            + "FrameworkInfo lacks required principal: " + frameworkInfo.toString());
        }
        return frameworkInfo.getPrincipal();
    }
}
