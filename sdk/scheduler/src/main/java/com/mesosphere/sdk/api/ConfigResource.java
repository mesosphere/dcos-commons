package com.mesosphere.sdk.api;

import com.codahale.metrics.annotation.Timed;
import com.mesosphere.sdk.api.types.PrettyJsonResource;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.UUID;

import static com.mesosphere.sdk.api.ResponseUtils.jsonOkResponse;
import static com.mesosphere.sdk.api.ResponseUtils.jsonResponseBean;

/**
 * A read-only API for accessing active and inactive configurations from persistent storage.
 *
 * @param <T> The configuration type which is being stored by the framework.
 */
@Path("/v1/configurations")
public class ConfigResource<T extends ConfigStore<?>> extends PrettyJsonResource {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final T configStore;

    public ConfigResource(T configStore) {
        this.configStore = configStore;
    }

    /**
     * Produces an ID listing of all stored configurations.
     */
    @GET
    @Timed
    public Response getConfigurationIds() {
        try {
            return jsonOkResponse(new JSONArray(configStore.list()));
        } catch (Exception ex) {
            logger.error("Failed to fetch list of configuration ids", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the content of the provided configuration ID, or returns an error if that ID doesn't
     * exist or the data couldn't be read.
     */
    @Path("/{configurationId}")
    @GET
    @Timed
    public Response getConfiguration(@PathParam("configurationId") String configurationId) {
        logger.info("Attempting to fetch config with id '{}'", configurationId);
        UUID uuid;
        try {
            uuid = UUID.fromString(configurationId);
        } catch (Exception ex) {
            logger.warn(String.format(
                    "Failed to parse requested configuration id '%s' as a UUID", configurationId), ex);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            return fetchConfig(uuid);
        } catch (ConfigStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                logger.warn(String.format("Requested configuration '%s' doesn't exist", configurationId), ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            logger.error(String.format(
                    "Failed to fetch requested configuration with id '%s'", configurationId), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the ID of the current target configuration, or returns an error if reading that
     * data failed.
     */
    @Path("/targetId")
    @GET
    @Timed
    public Response getTargetId() {
        try {
            // return a JSONArray to line up with getConfigurationIds()
            JSONArray configArray = new JSONArray(Arrays.asList(configStore.getTargetConfig()));
            return jsonOkResponse(configArray);
        } catch (ConfigStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                logger.warn("No target configuration exists", ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            logger.error("Failed to fetch target configuration", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the content of the current target configuration, or returns an error if reading that
     * data failed.
     */
    @Path("/target")
    @GET
    @Timed
    public Response getTarget() {
        UUID targetId;
        try {
            targetId = configStore.getTargetConfig();
        } catch (ConfigStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                logger.warn("No target configuration exists", ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            logger.error("Failed to fetch ID of target configuration", ex);
            return Response.serverError().build();
        }
        try {
            return fetchConfig(targetId);
        } catch (ConfigStoreException ex) {
            // Return 500 even if exception is NOT_FOUND: The data should be present.
            logger.error(String.format("Failed to fetch target configuration '%s'", targetId), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Returns an HTTP response containing the content of the requested configuration.
     */
    private Response fetchConfig(UUID id) throws ConfigStoreException {
        // return the content provided by the config verbatim, treat as plaintext
        return jsonResponseBean(configStore.fetch(id), Response.Status.OK);
    }
}
