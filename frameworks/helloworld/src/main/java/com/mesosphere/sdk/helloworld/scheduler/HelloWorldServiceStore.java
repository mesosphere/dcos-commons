package com.mesosphere.sdk.helloworld.scheduler;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.json.JSONObject;

import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Example implementation of persistent storage which keeps track of which services have been added to a dynamic
 * multi-scheduler. Ultimately, this is a basic key/value store which is backed by a provided {@link Persister}.
 */
public class HelloWorldServiceStore {

    /**
     * An entry in the ServiceStore. Pairs the original service id (with any slashes left as-is) with the yaml name.
     */
    public static class Entry {

        private static final Charset CHARSET = StandardCharsets.UTF_8;

        // The original unmodified (without slashes converted to double-underscores). We store this so that we can
        // recover it following scheduler restart.
        private static final String SERVICE_ID_KEY = "original_service_id";

        // The name of the yaml example to be used by the service.
        private static final String YAML_NAME_KEY = "yaml_name";

        // The original service ID, which may contain slashes.
        public final String serviceId;

        // The name of the YAML example to be executed.
        public final String yamlName;

        private Entry(String serviceId, String yamlName) {
            this.serviceId = serviceId;
            this.yamlName = yamlName;
        }

        private static Entry deserialize(byte[] data) {
            JSONObject json = new JSONObject(new String(data, CHARSET));
            return new Entry(json.getString(SERVICE_ID_KEY), json.getString(YAML_NAME_KEY));
        }

        private byte[] serialize() {
            return new JSONObject()
                    .put(SERVICE_ID_KEY, serviceId)
                    .put(YAML_NAME_KEY, yamlName)
                    .toString()
                    .getBytes(CHARSET);
        }
    }

    private static final String SERVICE_PATH_NAME = "AddedServices";

    private final Persister persister;

    HelloWorldServiceStore(Persister persister) {
        this.persister = persister;
    }

    /**
     * This is called to get information about a specific service when listing services.
     * Returns the specified entry value if it exists, or an empty {@link Optional} if it doesn't.
     *
     * @throws PersisterException in the event of issues with storage access
     */
    public Optional<String> get(String serviceId) throws PersisterException {
        try {
            byte[] data = persister.get(getSanitizedPath(serviceId));
            return Optional.of(Entry.deserialize(data).yamlName);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                return Optional.empty();
            } else {
                throw e;
            }
        }
    }

    /**
     * This called to recover the list of running services.
     * Returns a mapping of all service ids to yaml names which are currently listed in the store.
     *
     * @throws PersisterException in the event of issues with storage access
     */
    public Map<String, String> list() throws PersisterException {
        Collection<String> children;
        try {
            children = persister.getChildren(SERVICE_PATH_NAME);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                return Collections.emptyMap();
            } else {
                throw e;
            }
        }

        Map<String, String> entries = new TreeMap<>();
        for (String childNode : children) {
            Entry entry = Entry.deserialize(persister.get(getRawPath(childNode)));
            entries.put(entry.serviceId, entry.yamlName);
        }
        return entries;
    }

    /**
     * This is called when the user has submitted a new service to be run, or wants to replace an existing service with
     * a different config.
     * Stores the provided entry in the underlying persister.
     *
     * @throws PersisterException in the event of issues with storage access
     */
    public void put(String serviceId, String yamlName) throws PersisterException {
        persister.set(getSanitizedPath(serviceId), new Entry(serviceId, yamlName).serialize());
    }

    /**
     * This is called after an uninstall has completed.
     * Removes the specified entry if it exists, or does nothing if it doesn't exist.
     *
     * @throws PersisterException in the event of issues with storage access
     */
    public void remove(String serviceId) throws PersisterException {
        try {
            persister.recursiveDelete(getSanitizedPath(serviceId));
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // no-op
            } else {
                throw e;
            }
        }
    }

    private static String getSanitizedPath(String serviceId) {
        return getRawPath(SchedulerUtils.withEscapedSlashes(serviceId));
    }

    private static String getRawPath(String serviceNodeName) {
        return PersisterUtils.join(SERVICE_PATH_NAME, serviceNodeName);
    }
}
