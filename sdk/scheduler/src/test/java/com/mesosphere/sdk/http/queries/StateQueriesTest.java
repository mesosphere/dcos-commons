package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.state.StateStoreUtilsTest;
import com.mesosphere.sdk.storage.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.*;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.types.StringPropertyDeserializer;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StateQueriesTest {
    @Mock private FrameworkStore mockFrameworkStore;
    @Mock private StateStore mockStateStore;
    @Mock private Persister mockPersister;
    @Mock private PersisterCache mockPersisterCache;
    private static final String FILE_NAME = "test-file";
    private static final String FILE_CONTENT = "test data";

    private StateStore stateStore;
    private Persister persister;
    @Mock FormDataContentDisposition formDataContentDisposition;

    @Before
    public void beforeEach() {
        persister = new MemPersister();
        stateStore = new StateStore(persister);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetFrameworkId() {
        FrameworkID id = FrameworkID.newBuilder().setValue("aoeu-asdf").build();
        when(mockFrameworkStore.fetchFrameworkId()).thenReturn(Optional.of(id));
        Response response = StateQueries.getFrameworkId(mockFrameworkStore);
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(1, json.length());
        assertEquals(id.getValue(), json.get(0));
    }

    @Test
    public void testGetFrameworkIdMissing() {
        when(mockFrameworkStore.fetchFrameworkId()).thenReturn(Optional.empty());
        Response response = StateQueries.getFrameworkId(mockFrameworkStore);
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGetFrameworkIdFails() {
        when(mockFrameworkStore.fetchFrameworkId()).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = StateQueries.getFrameworkId(mockFrameworkStore);
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetPropertyKeys() {
        when(mockStateStore.fetchPropertyKeys()).thenReturn(Arrays.asList("hi", "hey"));
        Response response = StateQueries.getPropertyKeys(mockStateStore);
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(2, json.length());
        assertEquals("hi", json.get(0));
        assertEquals("hey", json.get(1));
    }

    @Test
    public void testGetPropertyKeysEmpty() {
        when(mockStateStore.fetchPropertyKeys()).thenReturn(Collections.emptyList());
        Response response = StateQueries.getPropertyKeys(mockStateStore);
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(0, json.length());
    }

    @Test
    public void testGetPropertyKeysFails() {
        when(mockStateStore.fetchPropertyKeys()).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = StateQueries.getPropertyKeys(mockStateStore);
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetProperty() {
        byte[] property = "hello this is a property".getBytes(StandardCharsets.UTF_8);
        when(mockStateStore.fetchProperty("foo")).thenReturn(property);
        Response response = StateQueries.getProperty(mockStateStore, new StringPropertyDeserializer(), "foo");
        assertEquals(200, response.getStatus());
        assertEquals("hello this is a property", response.getEntity());
    }

    @Test
    public void testGetPropertyMissing() {
        when(mockStateStore.fetchProperty("foo")).thenThrow(new StateStoreException(Reason.NOT_FOUND, "hi"));
        Response response = StateQueries.getProperty(mockStateStore, new StringPropertyDeserializer(), "foo");
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGetPropertyFails() {
        when(mockStateStore.fetchProperty("foo")).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = StateQueries.getProperty(mockStateStore, new StringPropertyDeserializer(), "foo");
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testRefreshCache() throws PersisterException {
        when(mockStateStore.getPersister()).thenReturn(mockPersisterCache);
        Response response = StateQueries.refreshCache(mockStateStore);
        assertEquals(200, response.getStatus());
        validateCommandResult(response, "refresh");
        verify(mockPersisterCache).refresh();
    }

    @Test
    public void testRefreshCacheNotCached() {
        when(mockStateStore.getPersister()).thenReturn(mockPersister);
        Response response = StateQueries.refreshCache(mockStateStore);
        assertEquals(409, response.getStatus());
    }

    @Test
    public void testRefreshCacheFailure() throws PersisterException {
        when(mockStateStore.getPersister()).thenReturn(mockPersisterCache);
        doThrow(new PersisterException(Reason.UNKNOWN, "hi")).when(mockPersisterCache).refresh();
        Response response = StateQueries.refreshCache(mockStateStore);
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testStoreAndGetFile() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));
        when(formDataContentDisposition.getSize()).thenReturn((long)FILE_CONTENT.length());
        StateQueries.storeFile(stateStore, FILE_NAME, inputStream, formDataContentDisposition);
        Response response = StateQueries.getFile(stateStore, FILE_NAME);
        assertEquals(200, response.getStatus());
        assertEquals(FILE_CONTENT, response.getEntity());
    }

    @Test
    public void testStoreAndListFiles() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));
        when(formDataContentDisposition.getSize()).thenReturn((long)FILE_CONTENT.length());
        StateQueries.storeFile(stateStore, FILE_NAME + "-1", inputStream, formDataContentDisposition);
        StateQueries.storeFile(stateStore, FILE_NAME + "-2", inputStream, formDataContentDisposition);
        Collection<String> file_names = new HashSet<>();
        file_names.add(FILE_NAME + "-1");
        file_names.add(FILE_NAME + "-2");
        assertEquals(file_names, StateQueries.getFileNames(stateStore));
    }

    @Test
    public void testPutAndGetFile() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));
        when(formDataContentDisposition.getFileName()).thenReturn(FILE_NAME);
        when(formDataContentDisposition.getSize()).thenReturn((long)FILE_CONTENT.length());
        when(mockStateStore.fetchProperty(StateQueries.FILE_NAME_PREFIX + FILE_NAME))
                .thenReturn(FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));

        Response response = StateQueries.putFile(mockStateStore,inputStream, formDataContentDisposition);
        assertEquals(200, response.getStatus());
        response = StateQueries.getFile(mockStateStore, FILE_NAME);
        assertEquals(200, response.getStatus());
        assertEquals(FILE_CONTENT, response.getEntity());
    }

    @Test
    public void testBadUpload() throws IOException {
        int fileSize = StateQueries.FILE_SIZE / FILE_CONTENT.length() * 100;
        String input = String.join("", Collections.nCopies(fileSize, FILE_CONTENT));
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StateQueries.FILE_ENCODING));
        when(formDataContentDisposition.getFileName()).thenReturn(FILE_NAME);
        when(formDataContentDisposition.getSize()).thenReturn(((long)fileSize));
        when(mockStateStore.fetchProperty(StateQueries.FILE_NAME_PREFIX + FILE_NAME))
                .thenReturn(FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));

        Response response = StateQueries.putFile(mockStateStore, inputStream, formDataContentDisposition);
        assertEquals(400, response.getStatus());
        assertEquals(StateQueries.UPLOAD_TOO_BIG_ERROR_MESSAGE, response.getEntity());
    }

    @Test
    public void testNoFileUpload() throws IOException {
        when(formDataContentDisposition.getFileName()).thenReturn(FILE_NAME);
        when(formDataContentDisposition.getSize()).thenReturn((long)0);
        when(mockStateStore.fetchProperty(StateQueries.FILE_NAME_PREFIX + FILE_NAME))
                .thenReturn(FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));

        Response response = StateQueries.putFile(mockStateStore, null, formDataContentDisposition);
        assertEquals(400, response.getStatus());
        assertEquals(StateQueries.NO_FILE_ERROR_MESSAGE, response.getEntity());
    }

    @Test
    public void testFailedUpload() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));
        when(formDataContentDisposition.getFileName()).thenReturn(FILE_NAME);
        when(formDataContentDisposition.getSize()).thenReturn(
                (long)FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING).length);
        doThrow(new StateStoreException(new PersisterException(Reason.STORAGE_ERROR, "Failed to store")))
                .when(mockStateStore).storeProperty(
                        StateQueries.FILE_NAME_PREFIX + FILE_NAME,
                        FILE_CONTENT.getBytes(StateQueries.FILE_ENCODING));

        Response response = StateQueries.putFile(mockStateStore, inputStream, formDataContentDisposition);
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGettingTasksZones() {
        TaskInfo taskInfo = createTaskInfoWithZone(TestConstants.TASK_NAME, TestConstants.ZONE);
        when(mockStateStore.fetchTaskNames()).thenReturn(Arrays.asList(TestConstants.TASK_NAME));
        when(mockStateStore.fetchTask(TestConstants.TASK_NAME)).thenReturn(Optional.of(taskInfo));
        Response response = StateQueries.getTaskNamesToZones(mockStateStore);

        Map<String, String> expectedTaskNameToZone = new HashMap<>();
        expectedTaskNameToZone.put(TestConstants.TASK_NAME,  TestConstants.ZONE);
        assertEquals(
                response.getEntity(),
                ResponseUtils.jsonOkResponse(new JSONObject(expectedTaskNameToZone)).getEntity()
        );
    }

    @Test
    public void testGettingSpecificTaskZone() {
        TaskInfo taskInfo = createTaskInfoWithZone(TestConstants.TASK_NAME, TestConstants.ZONE);
        when(mockStateStore.fetchTaskNames()).thenReturn(Arrays.asList(TestConstants.TASK_NAME));
        when(mockStateStore.fetchTask(TestConstants.TASK_NAME)).thenReturn(Optional.of(taskInfo));
        Response response = StateQueries.getTaskNameToZone(mockStateStore, TestConstants.TASK_NAME);
        assertEquals(response.getEntity(), TestConstants.ZONE);
    }

    @Test
    public void testGettingSpecificTaskZoneWithIP() {
        TaskInfo taskInfo = createTaskInfoWithZone(TestConstants.TASK_NAME, TestConstants.ZONE);
        TaskStatus taskStatus = createTaskStatusWithIP(taskInfo, TestConstants.IP_ADDRESS);

        when(mockStateStore.fetchTaskNames()).thenReturn(Arrays.asList(TestConstants.TASK_NAME));
        when(mockStateStore.fetchTask(TestConstants.TASK_NAME)).thenReturn(Optional.of(taskInfo));
        when(mockStateStore.fetchStatus(TestConstants.TASK_NAME)).thenReturn(Optional.of(taskStatus));
        Response response = StateQueries.getTaskIPsToZones(
                mockStateStore,
                TestConstants.TASK_NAME.substring(0, 4), // simulates the pod-type prefix of a task name
                TestConstants.IP_ADDRESS
        );
        assertEquals(response.getEntity(), TestConstants.ZONE);
    }

    private static TaskStatus createTaskStatusWithIP(TaskInfo taskInfo, String ipAddress) {
        final TaskStatus taskStatus = StateStoreUtilsTest.newTaskStatus(taskInfo, TaskState.TASK_UNKNOWN);
        return TaskStatus.newBuilder(taskStatus)
                .setContainerStatus(ContainerStatus.newBuilder()
                        .addNetworkInfos(
                                NetworkInfo.newBuilder().addIpAddresses(
                                        NetworkInfo.IPAddress.newBuilder()
                                                .setIpAddress(ipAddress)
                                        .build()
                                ).build()
                        )
                ).build();
    }

    private static TaskInfo createTaskInfoWithZone(String taskName, String zone) {
        return TaskInfo.newBuilder(StateStoreUtilsTest.createTask(taskName))
                .setCommand(
                        CommandInfo.newBuilder()
                        .setEnvironment(
                                Environment.newBuilder()
                                .addVariables(
                                        Environment.Variable.newBuilder()
                                        .setName(EnvConstants.ZONE_TASKENV)
                                        .setValue(zone)
                                        .build()
                                ).build()
                        ).build()
                ).build();
    }

    private static void validateCommandResult(Response response, String commandName) {
        assertEquals("{\"message\": \"Received cmd: " + commandName + "\"}", response.getEntity().toString());
    }
}
