package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.types.PropertyDeserializer;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.commons.io.IOUtils;
import org.apache.mesos.Protos;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An API for reading task and frameworkId state from persistent storage, and resetting the state store cache if one is
 * being used.
 */
public class StateQueries {

    private static final Logger logger = LoggerFactory.getLogger(StateQueries.class);

    static final String FILE_NAME_PREFIX = "file-";
    static final Charset FILE_ENCODING = StandardCharsets.UTF_8;
    static final int FILE_SIZE = 1024; // state store shouldn't be holding big files anyway...
    static final String UPLOAD_TOO_BIG_ERROR_MESSAGE = "File size is restricted to " + FILE_SIZE + " bytes.";
    static final String NO_FILE_ERROR_MESSAGE = "Only the first 1024 bytes of a file can be uploaded.";

    private StateQueries() {
        // do not instantiate
    }

    /**
     * Produces the configured ID of the framework, or returns an error if reading that data failed.
     */
    public static Response getFrameworkId(FrameworkStore frameworkStore) {
        try {
            Optional<Protos.FrameworkID> frameworkIdOptional = frameworkStore.fetchFrameworkId();
            if (frameworkIdOptional.isPresent()) {
                JSONArray idArray = new JSONArray(Arrays.asList(frameworkIdOptional.get().getValue()));
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

    public static Response getFiles(StateStore stateStore) {
        try {
            logger.info("Getting all files");
            Collection<String> fileNames = getFileNames(stateStore);
            return ResponseUtils.plainOkResponse(fileNames.toString());
        } catch (StateStoreException e) {
            logger.error("Failed to get a list of files", e);
            return Response.serverError().build();
        }
    }

    public static Response getFile(StateStore stateStore, String fileName) {
        try {
            logger.info("Getting file {}", fileName);
            return ResponseUtils.plainOkResponse(getFileContent(stateStore, fileName));
        } catch (StateStoreException e) {
            logger.error("Failed to get file {}: {}", fileName, e);
            return ResponseUtils.plainResponse(
                    String.format("Failed to get the file"),
                    Response.Status.NOT_FOUND
            );
        } catch (UnsupportedEncodingException e) {
            logger.error("Cannot encode data: ", e);
            return Response.serverError().build();
        }
    }

    /**
     * Endpoint for uploading arbitrary files of size up to 1024 Bytes.
     * @param uploadedInputStream The input stream containing the data.
     * @param fileDetails The details of the file to upload.
     * @return response indicating result of put operation.
     */
    public static Response putFile(
            StateStore stateStore, InputStream uploadedInputStream, FormDataContentDisposition fileDetails) {
        logger.info(fileDetails.toString());
        String fileName = fileDetails.getFileName();
        if (uploadedInputStream == null || fileName == null) {
            return ResponseUtils.plainResponse(NO_FILE_ERROR_MESSAGE, Response.Status.BAD_REQUEST);
        }

        if (fileDetails.getSize() > FILE_SIZE) {
           return ResponseUtils.plainResponse(UPLOAD_TOO_BIG_ERROR_MESSAGE, Response.Status.BAD_REQUEST);
        }

        try {
            logger.info("Storing {}", fileName);
            storeFile(stateStore, fileName, uploadedInputStream, fileDetails);
            return Response.status(Response.Status.OK).build();
        } catch (StateStoreException | IOException e) {
            logger.error("Failed to store file {}: {}", fileName, e);
            return Response.serverError().build();
        }
    }

    /**
     * Returns the Zone information for all of the tasks of the service.
     */
    public static Response getTaskNamesToZones(StateStore stateStore) {
        try {
            return ResponseUtils.jsonOkResponse(new JSONObject(getTasksZones(stateStore)));
        } catch (StateStoreException ex) {
            logger.error("Failed to fetch the zone information for the service's tasks: ", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Returns the Zone information for a given task.
     */
    public static Response getTaskNameToZone(StateStore stateStore, String taskName) {
        try {
            Map<String, String> tasksZones = getTasksZones(stateStore);
            if (tasksZones.containsKey(taskName)) {
                return ResponseUtils.plainOkResponse(tasksZones.get(taskName));
            } else {
                logger.error("No zone exists for the specified task");
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (StateStoreException ex) {
            logger.error("Failed to fetch the zone information for the service's task: ", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Returns the Zone information for a given pod type and an IP address.
     */
    public static Response getTaskIPsToZones(StateStore stateStore, String podType, String ip) {
        try {
            String zone = getZoneFromTaskNameAndIP(stateStore, podType, ip);
            if (zone.isEmpty()) {
                logger.error("Failed to find a zone for pod type = %s, ip address = %s", podType, ip);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return ResponseUtils.plainOkResponse(zone);
        } catch (StateStoreException ex) {
            logger.error("Failed to fetch the zone information for the service's task: ", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces a list of known properties.
     */
    public static Response getPropertyKeys(StateStore stateStore) {
        try {
            JSONArray keyArray = new JSONArray(stateStore.fetchPropertyKeys());
            return ResponseUtils.jsonOkResponse(keyArray);
        } catch (StateStoreException ex) {
            logger.error("Failed to fetch list of property keys", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the value for the provided property, or returns an error if that property doesn't exist or the data
     * couldn't be read.
     */
    public static Response getProperty(StateStore stateStore, PropertyDeserializer propertyDeserializer, String key) {
        try {
            logger.info("Attempting to fetch property '{}'", key);
            return ResponseUtils.jsonResponseBean(
                    propertyDeserializer.toJsonString(key, stateStore.fetchProperty(key)), Response.Status.OK);
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
    public static Response refreshCache(StateStore stateStore) {
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

    /**
     * Retrieves the contents of a file based on file name.
     * @param stateStore The state store to get file content from.
     * @param fileName The name of the file to retrieve.
     * @return Contents of the file.
     * @throws UnsupportedEncodingException
     */
    private static String getFileContent(StateStore stateStore, String fileName) throws UnsupportedEncodingException {
        fileName = FILE_NAME_PREFIX + fileName;
        return new String(stateStore.fetchProperty(fileName), FILE_ENCODING);
    }

    /**
     * Stores the file in the state store.
     * @param stateStore The state store to store the file in.
     * @param fileName The name of the file to store.
     * @param uploadedInputStream The input stream holding the content of the file.
     */
    static void storeFile(
            StateStore stateStore,
            String fileName,
            InputStream uploadedInputStream,
            FormDataContentDisposition fileDetails) throws StateStoreException, IOException {
        StringWriter writer = new StringWriter();
        Reader reader = new InputStreamReader(uploadedInputStream, FILE_ENCODING);
        // only copy the number of bytes the metadata specifies
        IOUtils.copyLarge(reader, writer, 0, fileDetails.getSize());
        fileName = FILE_NAME_PREFIX + fileName;
        stateStore.storeProperty(fileName, writer.toString().getBytes(FILE_ENCODING));
    }

    /**
     * Gets the name of the files that are stored in the state store. The files stored in the state store are prefixed
     * with "file_".
     * @param stateStore The state store to get files names from.
     * @return The set of all file names stored.
     */
    static Collection<String> getFileNames(StateStore stateStore) {
        return stateStore.fetchPropertyKeys().stream()
                .filter(key -> key.startsWith(FILE_NAME_PREFIX))
                .map(file_name -> file_name.replaceFirst(FILE_NAME_PREFIX, ""))
                .collect(Collectors.toSet());
    }

    /**
     * Constructs a map of task names to zones indicating in what zone the respective task name is in.
     * @param stateStore The state store to get task infos from.
     * @return Returns the map of task names to zones.
     */
    private static Map<String, String> getTasksZones(StateStore stateStore) {
        Collection<String> taskNames = stateStore.fetchTaskNames();
        Map<String, String> tasksZones = new HashMap<>();
        for (String taskName : taskNames) {
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
            if (taskInfoOptional.isPresent() && TaskUtils.taskHasZone(taskInfoOptional.get())) {
                tasksZones.put(taskName, TaskUtils.getTaskZone(taskInfoOptional.get()));
            }
        }
        return tasksZones;
    }

    /**
     * Gets the zone of a pod given its pod type and the IP address of the pod.
     * @param stateStore The {@link StateStore} from which to get task info and task status from.
     * @param podType The type of the pod to get zone information for.
     * @param ipAddress The IP address of the pod to get zone information for.
     * @return A string indicating the zone of the pod.
     */
    private static String getZoneFromTaskNameAndIP(StateStore stateStore, String podType, String ipAddress) {
        Collection<String> taskNames = stateStore.fetchTaskNames();
        for (String taskName : taskNames) {
            if (!taskName.startsWith(podType)) {
                continue;
            }
            Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskName);
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
            if (!taskStatusOptional.isPresent() || !taskInfoOptional.isPresent()) {
                return "";
            }
            String taskIPAddress = TaskUtils.getTaskIPAddress(taskStatusOptional.get());
            if (TaskUtils.taskHasZone(taskInfoOptional.get()) && taskIPAddress.equals(ipAddress)) {
                return TaskUtils.getTaskZone(taskInfoOptional.get());
            }
        }
        return "";
    }
}
