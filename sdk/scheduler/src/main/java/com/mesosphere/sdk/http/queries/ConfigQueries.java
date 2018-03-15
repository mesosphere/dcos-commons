package com.mesosphere.sdk.http.queries;

import static com.mesosphere.sdk.http.ResponseUtils.jsonOkResponse;
import static com.mesosphere.sdk.http.ResponseUtils.jsonResponseBean;

import java.util.Arrays;
import java.util.UUID;

import javax.ws.rs.core.Response;

import com.mesosphere.sdk.config.Configuration;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A read-only API for accessing active and inactive configurations from persistent storage.
 */
public class ConfigQueries {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactQueries.class);

    private ConfigQueries() {
        // Do not instantiate
    }

    /**
     * Produces an ID listing of all stored configurations.
     *
     * @param <T> The configuration type which is being stored by the service.
     */
    public static <T extends Configuration> Response getConfigurationIds(ConfigStore<T> configStore) {
        try {
            return jsonOkResponse(new JSONArray(configStore.list()));
        } catch (Exception ex) {
            LOGGER.error("Failed to fetch list of configuration ids", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the content of the provided configuration ID, or returns an error if that ID doesn't
     * exist or the data couldn't be read.
     */
    public static <T extends Configuration> Response getConfiguration(
            ConfigStore<T> configStore, String configurationId) {
        LOGGER.info("Attempting to fetch config with id '{}'", configurationId);
        UUID uuid;
        try {
            uuid = UUID.fromString(configurationId);
        } catch (Exception ex) {
            LOGGER.warn(String.format(
                    "Failed to parse requested configuration id '%s' as a UUID", configurationId), ex);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            return fetchConfig(configStore, uuid);
        } catch (ConfigStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                LOGGER.warn(String.format("Requested configuration '%s' doesn't exist", configurationId), ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            LOGGER.error(String.format(
                    "Failed to fetch requested configuration with id '%s'", configurationId), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the ID of the current target configuration, or returns an error if reading that
     * data failed.
     */
    public static <T extends Configuration> Response getTargetId(ConfigStore<T> configStore) {
        try {
            // return a JSONArray to line up with getConfigurationIds()
            JSONArray configArray = new JSONArray(Arrays.asList(configStore.getTargetConfig()));
            return jsonOkResponse(configArray);
        } catch (ConfigStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                LOGGER.warn("No target configuration exists", ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            LOGGER.error("Failed to fetch target configuration", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the content of the current target configuration, or returns an error if reading that
     * data failed.
     */
    public static <T extends Configuration> Response getTarget(ConfigStore<T> configStore) {
        UUID targetId;
        try {
            targetId = configStore.getTargetConfig();
        } catch (ConfigStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                LOGGER.warn("No target configuration exists", ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            LOGGER.error("Failed to fetch ID of target configuration", ex);
            return Response.serverError().build();
        }
        try {
            return fetchConfig(configStore, targetId);
        } catch (ConfigStoreException ex) {
            // Return 500 even if exception is NOT_FOUND: The data should be present.
            LOGGER.error(String.format("Failed to fetch target configuration '%s'", targetId), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Returns an HTTP response containing the content of the requested configuration.
     */
    private static <T extends Configuration> Response fetchConfig(ConfigStore<T> configStore, UUID id)
            throws ConfigStoreException {
        // return the content provided by the config verbatim, treat as plaintext
        return jsonResponseBean(configStore.fetch(id), Response.Status.OK);
    }
}
