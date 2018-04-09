package com.mesosphere.sdk.scheduler.multi;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Default implementation of persistent storage which keeps track of the services that have been added to a dynamic
 * multi-scheduler. For each service entry, the developer is provided with
 */
public class ServiceStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceStore.class);
    private static final String ROOT_PATH_NAME = "ServiceList";

    private static final Charset ID_CHARSET = StandardCharsets.UTF_8;

    /**
     * The name of the node where the original (unsanitized) service id is stored (e.g. /ServiceList/sanitized.name/Id)
     *
     * <p>This is only needed so that we can recover original unsanitized names.
     */
    private static final String ID_NODE = "Id";

    /**
     * The name of the node where context data is stored (e.g. /ServiceList/sanitized.name/Context)
     */
    private static final String CONTEXT_NODE = "Context";

    /**
     * An arbitrary limit on the amount of context data to allow for each service. In practice, services are expected to
     * just store a small (<1KB) blob of JSON or similar that describes the service in the developer's own terms.
     *
     * <p>100KB is arbitrary, but in practice this must stay well under the default ZK limit of 1024KB. Ideally,
     * developers would only store e.g. a bit of JSON here which describes how the service should be constructed within
     * the context of their application.
     */
    private static final int CONTEXT_LENGTH_LIMIT_BYTES = 100 * 1024;

    private final Persister persister;
    private final ServiceFactory serviceFactory;

    public ServiceStore(Persister persister, ServiceFactory serviceFactory) {
        this.persister = persister;
        this.serviceFactory = serviceFactory;
    }

    /**
     * This is called to get the context information which was previously stored via {@link #put(String, byte[])}.
     * Returns the specified entry value if it exists, or an empty {@link Optional} if it doesn't. The byte array may be
     * {@code null} if service entry exists, but the context data was {@code null} when originally passed via
     * {@link #put(String, byte[])}.
     *
     * @throws PersisterException in the event of issues with storage access
     */
    public Optional<byte[]> get(String serviceId) throws PersisterException {
        try {
            return Optional.of(persister.get(getSanitizedServiceContextPath(serviceId)));
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw e;
            }
        }
    }

    /**
     * This should be invoked during Scheduler process initialization to recover any currently-running services.
     * Returns a list of reconstructed service objects. Any services which failed to be reconstructed (due to problems
     * in the developer-provided factory) are omitted from the returned list.
     *
     * @throws PersisterException in the event of issues with storage access
     */
    public Collection<AbstractScheduler> recover() throws PersisterException {
        Collection<String> children;
        try {
            children = persister.getChildren(ROOT_PATH_NAME);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                return Collections.emptyList(); // Nothing to recover, no-op
            } else {
                throw e;
            }
        }

        Collection<AbstractScheduler> recovered = new ArrayList<>();
        for (String child : children) {
            String idPath = getRawServiceIdPath(child);
            String contextPath = getRawServiceContextPath(child);
            Map<String, byte[]> childEntries = persister.getMany(Arrays.asList(idPath, contextPath));
            byte[] idData = childEntries.get(idPath);
            if (idData == null) {
                // ID should always be present (whereas context data may be missing if the developer isn't using it).
                // Complain and move on. Maybe the operator can fix it.
                LOGGER.error("Missing ID data at {} during service recovery, continuing without this service", idPath);
                continue;
            }
            byte[] contextData = childEntries.get(contextPath);
            String idStr = new String(idData, ID_CHARSET);
            LOGGER.info("Recovering prior service: {}", idStr);
            try {
                recovered.add(serviceFactory.buildService(idStr, contextData));
            } catch (Exception e) {
                LOGGER.error(String.format(
                        "Unable to reconstruct service %s during recovery, continuing without this service", idStr), e);
                continue;
            }
        }
        return recovered;
    }

    /**
     * This is called when the user has submitted a new service to be run, or wants to replace an existing service with
     * a different config. Generates the service object, THEN stores the data after it's shown to have worked. Returns
     * the resulting service object which can then be passed to the {@link MultiServiceManager} to start running the
     * service.
     *
     * @param serviceId the service id to store the data against -- must be unique among running services
     * @param context an arbitrary blob of context data to be passed to the developer's {@link ServiceFactory} when
     *                recovering this service, or {@code null} for {@code null} to be passed to the
     *                {@link ServiceFactory}, which is no greater than 100KB in length (100 * 1024 bytes)
     * @return the resulting scheduler object which may then be added to the {@link MultiServiceManager}
     * @throws Exception if there are issues with storage access, if the {@code context} exceeds 100KB, or if generating
     *                   the service using the factory fails
     */
    public AbstractScheduler put(String serviceId, byte[] context) throws Exception {
        Map<String, byte[]> serviceData = new TreeMap<>();
        serviceData.put(getSanitizedServiceIdPath(serviceId), serviceId.getBytes(ID_CHARSET));

        if (context != null && context.length > CONTEXT_LENGTH_LIMIT_BYTES) {
            throw new IllegalArgumentException(String.format(
                    "Provided context for service='%s' is %d bytes, but limit is %d bytes",
                    serviceId, context.length, CONTEXT_LENGTH_LIMIT_BYTES));
        }
        serviceData.put(getSanitizedServiceContextPath(serviceId), context);

        // We intentionally structure things where we exercise construction of the service before passing the data to
        // the underlying persister to be stored. This protects the developer from storing data which can't be handled
        // by their own factory implementation.
        AbstractScheduler service = serviceFactory.buildService(serviceId, context);

        persister.setMany(serviceData);
        LOGGER.info("Added service: {}", serviceId);
        return service;
    }

    /**
     * Returns an uninstall callback which should be invoked when an added service is ready to be cleaned up.
     *
     * This callback may be passed to the {@link MultiServiceEventClient}.
     */
    public MultiServiceEventClient.UninstallCallback getUninstallCallback() {
        return new MultiServiceEventClient.UninstallCallback() {
            @Override
            public void uninstalled(String name) {
                LOGGER.info("Service has completed uninstall, removing from ServiceStore: {}", name);
                try {
                    remove(name);
                } catch (PersisterException e) {
                    LOGGER.error(String.format("Failed to clean up uninstalled service %s", name), e);
                }
            }
        };
    }

    /**
     * This is called after an uninstall has completed.
     * Removes the specified entry if it exists, or does nothing if it doesn't exist.
     *
     * @throws PersisterException in the event of issues with storage access
     */
    private void remove(String serviceId) throws PersisterException {
        try {
            persister.recursiveDelete(getSanitizedServiceBasePath(serviceId));
            LOGGER.info("Removed service: {}", serviceId);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                LOGGER.info("No service found, skipping removal: {}", serviceId);
                // no-op
            } else {
                throw e;
            }
        }
    }

    private static String getSanitizedServiceContextPath(String serviceId) {
        return PersisterUtils.join(getSanitizedServiceBasePath(serviceId), CONTEXT_NODE);
    }

    private static String getRawServiceContextPath(String serviceId) {
        return PersisterUtils.join(getRawServiceBasePath(serviceId), CONTEXT_NODE);
    }

    private static String getSanitizedServiceIdPath(String serviceId) {
        return PersisterUtils.join(getSanitizedServiceBasePath(serviceId), ID_NODE);
    }

    private static String getRawServiceIdPath(String serviceId) {
        return PersisterUtils.join(getRawServiceBasePath(serviceId), ID_NODE);
    }

    private static String getSanitizedServiceBasePath(String serviceId) {
        return getRawServiceBasePath(SchedulerUtils.withEscapedSlashes(serviceId));
    }

    private static String getRawServiceBasePath(String serviceNodeName) {
        return PersisterUtils.join(ROOT_PATH_NAME, serviceNodeName);
    }
}
