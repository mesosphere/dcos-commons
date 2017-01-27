package com.mesosphere.sdk.api;

import org.apache.mesos.Protos.*;

import com.mesosphere.sdk.api.types.StringPropertyDeserializer;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class StateResourceTest {
    @Mock private StateStore mockStateStore;

    private StateResource resource;

    @Before
    public void beforeAll() {
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
        assertEquals(204, response.getStatus());
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
}
