package com.mesosphere.sdk.api;

import com.google.inject.Inject;
import com.mesosphere.sdk.api.types.PropertyDeserializer;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Optional;

/**
 * A read-only API for accessing task and frameworkId state from persistent storage.
 */
@Path("/v1/state")
public class StateResource {

    private static final Logger logger = LoggerFactory.getLogger(StateResource.class);

    private final StateStore stateStore;
    private final PropertyDeserializer propertyDeserializer;

    /**
     * Creates a new StateResource which cannot deserialize Properties. Callers will receive a
     * "204 NO_CONTENT" HTTP response when attempting to view the content of a property.
     *
     * @param stateStore the source of data to be returned to callers
     */
    public StateResource(StateStore stateStore) {
        this(stateStore, null);
    }

    /**
     * Creates a new StateResource which can deserialize Properties. Callers will be able to view
     * the content of individual Properties.
     *
     * @param stateStore the source of data to be returned to callers
     * @param propertyDeserializer a deserializer which can turn any Property in the provided
     *                             {@code stateStore} to valid JSON
     */
    @Inject
    public StateResource(StateStore stateStore, PropertyDeserializer propertyDeserializer) {
        this.stateStore = stateStore;
        this.propertyDeserializer = propertyDeserializer;
    }

    /**
     * Produces the configured ID of the framework, or returns an error if reading that data failed.
     */
    @Path("/frameworkId")
    @GET
    public Response getFrameworkId() {
        try {
            Optional<Protos.FrameworkID> frameworkIDOptional = stateStore.fetchFrameworkId();
            if (frameworkIDOptional.isPresent()) {
                JSONArray idArray = new JSONArray(Arrays.asList(frameworkIDOptional.get().getValue()));
                return Response.ok(idArray.toString(), MediaType.APPLICATION_JSON).build();
            } else {
                logger.warn("No framework ID exists");
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (StateStoreException ex) {
            logger.error("Failed to fetch framework ID", ex);
            return Response.serverError().build();
        }

    }

    @Path("/properties")
    @GET
    public Response getPropertyKeys() {
        try {
            JSONArray keyArray = new JSONArray(stateStore.fetchPropertyKeys());
            return Response.ok(keyArray.toString(), MediaType.APPLICATION_JSON).build();
        } catch (StateStoreException ex) {
            logger.error("Failed to fetch list of property keys", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the TaskInfo for the provided task name, or returns an error if that name doesn't
     * exist or the data couldn't be read.
     */
    @Path("/properties/{key}")
    @GET
    public Response getProperty(@PathParam("key") String key) {
        try {
            if (propertyDeserializer == null) {
                logger.warn("Cannot deserialize requested Property '{}': " +
                        "No deserializer was provided to StateResource constructor", key);
                return Response.noContent().build(); // 204 NO_CONTENT
            } else {
                logger.info("Attempting to fetch property '{}'", key);
                return Response.ok(propertyDeserializer.toJsonString(key, stateStore.fetchProperty(key)),
                        MediaType.APPLICATION_JSON).build();
            }
        } catch (StateStoreException ex) {
            if (ex.getReason() == Reason.NOT_FOUND) {
                logger.warn(String.format("Requested property '%s' wasn't found", key), ex);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            logger.error(String.format("Failed to fetch requested property '%s'", key), ex);
            return Response.serverError().build();
        }
    }
}
