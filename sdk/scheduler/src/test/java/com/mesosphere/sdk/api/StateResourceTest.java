package com.mesosphere.sdk.api;

import com.mesosphere.sdk.storage.MemPersister;
import org.apache.mesos.Protos.*;

import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.json.JSONArray;
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

public class StateResourceTest {
    @Mock private StateStore mockStateStore;
    @Mock private Persister mockPersister;
    @Mock private PersisterCache mockPersisterCache;
    private static final String FILE_NAME = "test-file";
    private static final String FILE_CONTENT = "test data";

    private StateResource resource;
    @Mock FormDataContentDisposition formDataContentDisposition;

    @Before
    public void beforeEach() {
        this.mockPersister = new MemPersister();
        this.mockStateStore = new StateStore(this.mockPersister);
        MockitoAnnotations.initMocks(this);
        resource = new StateResource(mockStateStore, new StringPropertyDeserializer());
    }

    @Test
    public void testGetFrameworkId() {
        FrameworkID id = FrameworkID.newBuilder().setValue("aoeu-asdf").build();
        when(mockStateStore.fetchFrameworkId()).thenReturn(Optional.of(id));
        Response response = resource.getFrameworkId();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(1, json.length());
        assertEquals(id.getValue(), json.get(0));
    }

    @Test
    public void testGetFrameworkIdMissing() {
        when(mockStateStore.fetchFrameworkId()).thenReturn(Optional.empty());
        Response response = resource.getFrameworkId();
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGetFrameworkIdFails() {
        when(mockStateStore.fetchFrameworkId()).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = resource.getFrameworkId();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetPropertyKeys() {
        when(mockStateStore.fetchPropertyKeys()).thenReturn(Arrays.asList("hi", "hey"));
        Response response = resource.getPropertyKeys();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(2, json.length());
        assertEquals("hi", json.get(0));
        assertEquals("hey", json.get(1));
    }

    @Test
    public void testGetPropertyKeysEmpty() {
        when(mockStateStore.fetchPropertyKeys()).thenReturn(Collections.emptyList());
        Response response = resource.getPropertyKeys();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(0, json.length());
    }

    @Test
    public void testGetPropertyKeysFails() {
        when(mockStateStore.fetchPropertyKeys()).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = resource.getPropertyKeys();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetProperty() {
        byte[] property = "hello this is a property".getBytes(StandardCharsets.UTF_8);
        when(mockStateStore.fetchProperty("foo")).thenReturn(property);
        Response response = resource.getProperty("foo");
        assertEquals(200, response.getStatus());
        assertEquals("hello this is a property", response.getEntity());
    }

    @Test
    public void testGetPropertyNoDeserializer() {
        Response response = new StateResource(mockStateStore).getProperty("foo");
        assertEquals(409, response.getStatus());
    }

    @Test
    public void testGetPropertyMissing() {
        when(mockStateStore.fetchProperty("foo")).thenThrow(new StateStoreException(Reason.NOT_FOUND, "hi"));
        Response response = resource.getProperty("foo");
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGetPropertyFails() {
        when(mockStateStore.fetchProperty("foo")).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = resource.getProperty("foo");
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testRefreshCache() throws PersisterException {
        when(mockStateStore.getPersister()).thenReturn(mockPersisterCache);
        Response response = resource.refreshCache();
        assertEquals(200, response.getStatus());
        validateCommandResult(response, "refresh");
        verify(mockPersisterCache).refresh();
    }

    @Test
    public void testRefreshCacheNotCached() {
        when(mockStateStore.getPersister()).thenReturn(mockPersister);
        Response response = resource.refreshCache();
        assertEquals(409, response.getStatus());
    }

    @Test
    public void testRefreshCacheFailure() throws PersisterException {
        when(mockStateStore.getPersister()).thenReturn(mockPersisterCache);
        doThrow(new PersisterException(Reason.UNKNOWN, "hi")).when(mockPersisterCache).refresh();
        Response response = resource.refreshCache();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testStoreAndGetFile() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(FILE_CONTENT.getBytes(StateResource.FILE_ENCODING));
        when(formDataContentDisposition.getSize()).thenReturn((long)FILE_CONTENT.length());
        StateResource.storeFile(mockStateStore, FILE_NAME, inputStream, formDataContentDisposition);
        assertEquals(StateResource.getFile(mockStateStore, FILE_NAME), FILE_CONTENT);
    }

    @Test
    public void testStoreAndListFiles() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(FILE_CONTENT.getBytes(StateResource.FILE_ENCODING));
        when(formDataContentDisposition.getSize()).thenReturn((long)FILE_CONTENT.length());
        StateResource.storeFile(mockStateStore, FILE_NAME + "-1", inputStream, formDataContentDisposition);
        StateResource.storeFile(mockStateStore, FILE_NAME + "-2", inputStream, formDataContentDisposition);
        Collection<String> file_names = new HashSet<>();
        file_names.add(FILE_NAME + "-1");
        file_names.add(FILE_NAME + "-2");
        assertEquals(StateResource.getFileNames(mockStateStore), file_names);
    }

    @Test
    public void testPutAndGetFile() throws IOException {
        InputStream inputStream = new ByteArrayInputStream(FILE_CONTENT.getBytes(StateResource.FILE_ENCODING));
        when(formDataContentDisposition.getFileName()).thenReturn(FILE_NAME);
        when(formDataContentDisposition.getSize()).thenReturn((long)FILE_CONTENT.length());
        when(mockStateStore.fetchProperty(StateResource.FILE_NAME_PREFIX + FILE_NAME))
                .thenReturn(FILE_CONTENT.getBytes(StateResource.FILE_ENCODING));

        Response response = resource.putFile(inputStream, formDataContentDisposition);
        assertEquals(200, response.getStatus());
        response = resource.getFile(FILE_NAME);
        assertEquals(200, response.getStatus());
        assertEquals(FILE_CONTENT, response.getEntity());
    }

    @Test
    public void testBadUpload() throws IOException {
        int fileSize = StateResource.FILE_SIZE / FILE_CONTENT.length() * 100;
        String input = String.join("", Collections.nCopies(fileSize, FILE_CONTENT));
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StateResource.FILE_ENCODING));
        when(formDataContentDisposition.getFileName()).thenReturn(FILE_NAME);
        when(formDataContentDisposition.getSize()).thenReturn(((long)fileSize));
        when(mockStateStore.fetchProperty(StateResource.FILE_NAME_PREFIX + FILE_NAME))
                .thenReturn(FILE_CONTENT.getBytes(StateResource.FILE_ENCODING));

        Response response = resource.putFile(inputStream, formDataContentDisposition);
        assertEquals(400, response.getStatus());
        assertEquals(StateResource.UPLOAD_TOO_BIG_ERROR_MESSAGE, response.getEntity());
    }


    private static void validateCommandResult(Response response, String commandName) {
        assertEquals("{\"message\": \"Received cmd: " + commandName + "\"}", response.getEntity().toString());
    }
}
