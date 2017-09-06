package com.mesosphere.sdk.api;

import com.google.inject.Inject;
import com.mesosphere.sdk.api.types.PropertyDeserializer;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.commons.io.IOUtils;
import org.apache.mesos.Protos;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * An API for reading task and frameworkId state from persistent storage, and resetting the state store cache if one is
 * being used.
 */
@Path("/v1/state")
public class StateResource {

    private static final Logger logger = LoggerFactory.getLogger(StateResource.class);
    private static final String VERSION_FILE = "versionFile";
    private static final String VERSION_FILE_ENCODING = "UTF-8";

    private final StateStore stateStore;
    private final PropertyDeserializer propertyDeserializer;

    /**
     * Creates a new StateResource which cannot deserialize Properties. Callers will receive a
     * "204 NO_CONTENT" HTTP response when attempting to view the content of a property.
     *
     * @param stateStore     the source of data to be returned to callers
     */
    public StateResource(StateStore stateStore) {
        this(stateStore, null);
    }

    /**
     * Creates a new StateResource which can deserialize Properties. Callers will be able to view
     * the content of individual Properties.
     *
     * @param stateStore           the source of data to be returned to callers
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
                return ResponseUtils.jsonOkResponse(idArray);
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
            return ResponseUtils.jsonOkResponse(keyArray);
        } catch (StateStoreException ex) {
            logger.error("Failed to fetch list of property keys", ex);
            return Response.serverError().build();
        }
    }

    @Path(VERSION_FILE)
    @Produces(MediaType.TEXT_PLAIN)
    @GET
    public Response getVersionFile() {
        try {
            logger.info("Getting version file");
            String versionFileContent =
                    new String(stateStore.fetchProperty(VERSION_FILE), VERSION_FILE_ENCODING);
            return ResponseUtils.plainOkResponse(versionFileContent);
        } catch (StateStoreException e) {
            logger.error("Failed to fetch the version file", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (UnsupportedEncodingException e) {
            logger.error("Cannot encode data: ", e);
            return Response.serverError().build();
        }
    }

    @Path(VERSION_FILE)
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response putVersionFile(@FormDataParam("file") InputStream uploadedInputStream) {
        try {
            if (stateStore.hasProperty(VERSION_FILE)) {
                logger.info("Version file already exists");
                return Response.status(Response.Status.OK).build();
            }
            logger.info("Putting version file");
            StringWriter stringWriter = new StringWriter();
            IOUtils.copy(uploadedInputStream, stringWriter, VERSION_FILE_ENCODING);
            stateStore.storeProperty(VERSION_FILE, stringWriter.toString().getBytes());
            logger.debug(stringWriter.toString());
            return Response.status(Response.Status.OK).build();
        } catch (Exception e) {
            logger.error("Failed to PUT version file", e);
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
                return Response.status(Response.Status.CONFLICT).build();
            } else {
                logger.info("Attempting to fetch property '{}'", key);
                return ResponseUtils.jsonResponseBean(
                        propertyDeserializer.toJsonString(key, stateStore.fetchProperty(key)), Response.Status.OK);
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

    /**
     * Refreshes the state store cache to reflect current data on ZK. Should only be needed if ZK was edited behind the
     * scheduler's back, or if there's a bug in the cache handling.
     */
    @Path("/refresh")
    @PUT
    public Response refreshCache() {
        PersisterCache cache = getPersisterCache(stateStore);
        if (cache == null) {
            logger.warn("State store is not cached: Refresh is not applicable");
            return Response.status(Response.Status.CONFLICT).build();
        }
        try {
            logger.info("Refreshing state store cache...");
            logger.info("Before:\n- tasks: {}\n- properties: {}",
                    stateStore.fetchTaskNames(), stateStore.fetchPropertyKeys());

            cache.refresh();

            logger.info("After:\n- tasks: {}\n- properties: {}",
                    stateStore.fetchTaskNames(), stateStore.fetchPropertyKeys());

            return ResponseUtils.jsonOkResponse(getCommandResult("refresh"));
        } catch (PersisterException ex) {
            logger.error("Failed to refresh state cache", ex);
            return Response.serverError().build();
        }
    }

    private static PersisterCache getPersisterCache(StateStore stateStore) {
        Persister persister = stateStore.getPersister();
        if (!(persister instanceof PersisterCache)) {
            return null;
        }
        return (PersisterCache) persister;
    }

    private static JSONObject getCommandResult(String command) {
        return new JSONObject(Collections.singletonMap(
                "message",
                String.format("Received cmd: %s", command)));
    }
}
