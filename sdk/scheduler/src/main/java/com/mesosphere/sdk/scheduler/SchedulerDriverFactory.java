package com.mesosphere.sdk.scheduler;

import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.mesosphere.mesos.HTTPAdapter.MesosToSchedulerDriverAdapter;
import com.mesosphere.mesos.protobuf.EvolverDevolver;
import com.mesosphere.sdk.dcos.Capabilities;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.MesosSchedulerDriver;
import org.apache.mesos.Protos.Credential;
import org.apache.mesos.Protos.FrameworkInfo;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.V0Mesos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating {@link MesosSchedulerDriver}s.
 */
public class SchedulerDriverFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerDriverFactory.class);

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
            Scheduler scheduler, FrameworkInfo frameworkInfo, String masterUrl, SchedulerConfig schedulerConfig) {
        return create(scheduler, frameworkInfo, masterUrl, schedulerConfig, null /* credentialSecret */);
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
            final SchedulerConfig schedulerConfig,
            final byte[] credentialSecret) {
        Credential credential;
        if (credentialSecret != null && credentialSecret.length > 0) {
            // User has manually provided a Secret. Provide a Credential with Principal + Secret.
            // (note: we intentionally avoid logging the content of the credential secret, just in case)
            LOGGER.info("Creating secret authenticated MesosSchedulerDriver for "
                    + "scheduler[{}], frameworkInfo[{}], masterUrl[{}], credentialSecret[{} bytes]",
                    scheduler, TextFormat.shortDebugString(frameworkInfo), masterUrl, credentialSecret.length);
            credential = Credential.newBuilder()
                    .setPrincipal(getPrincipal(frameworkInfo, "secret"))
                    .setSecretBytes(ByteString.copyFrom(credentialSecret))
                    .build();
        } else if (schedulerConfig.isSideChannelActive()) {
            // Sidechannel auth is enabled. Provide a Credential with only the Principal set.
            LOGGER.info("Creating sidechannel authenticated MesosSchedulerDriver for "
                    + "scheduler[{}], frameworkInfo[{}], masterUrl[{}]",
                    scheduler, TextFormat.shortDebugString(frameworkInfo), masterUrl);
            credential = Credential.newBuilder()
                    .setPrincipal(getPrincipal(frameworkInfo, "sidechannel"))
                    .build();
        } else {
            // No auth. Provide no credential.
            LOGGER.info("Creating unauthenticated MesosSchedulerDriver for "
                    + "scheduler[{}], frameworkInfo[{}], masterUrl[{}]",
                    scheduler, TextFormat.shortDebugString(frameworkInfo), masterUrl);
            credential = null;
        }
        return createInternal(scheduler, frameworkInfo, masterUrl, credential, schedulerConfig.getMesosApiVersion());
    }

    /**
     * Broken out into a separate function to allow testing with custom SchedulerDrivers.
     */
    protected SchedulerDriver createInternal(
            final Scheduler scheduler,
            final FrameworkInfo frameworkInfo,
            final String masterUrl,
            final Credential credential,
            final String mesosAPIVersion) {
        Capabilities capabilities = Capabilities.getInstance();
        if (credential != null) {
            return new MesosToSchedulerDriverAdapter(scheduler, frameworkInfo, masterUrl, true, credential) {
                @Override
                protected Mesos startInternal() {
                    if (capabilities.supportsV1APIByDefault()) {
                        return super.startInternal();
                    }

                    if (mesosAPIVersion.equals("V1")) {
                        LOGGER.warn(
                                "Current DC/OS cluster doesn't support the Mesos V1 API in strict mode. Using V0...");
                    }
                    return new V0Mesos(
                            this,
                            EvolverDevolver.evolve(frameworkInfo),
                            masterUrl,
                            EvolverDevolver.evolve(credential));
                }
            };
        }

        // Love too work around the fact that the MesosToSchedulerDriverAdapter both depends directly on the
        // process environment *and* uses two unrelated constructors for the case of credential being null
        return new MesosToSchedulerDriverAdapter(scheduler, frameworkInfo, masterUrl, true) {
            @Override
            protected Mesos startInternal() {
                if (capabilities.supportsV1APIByDefault()) {
                    return super.startInternal();
                }

                if (mesosAPIVersion.equals("V1")) {
                    LOGGER.warn(
                            "Current DC/OS cluster doesn't support the Mesos V1 API in strict mode. Using V0...");
                }
                return new V0Mesos(this, EvolverDevolver.evolve(frameworkInfo), masterUrl);
            }
        };
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
