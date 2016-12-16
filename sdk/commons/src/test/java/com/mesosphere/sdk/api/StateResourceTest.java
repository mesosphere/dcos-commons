package com.mesosphere.sdk.api;

import org.apache.mesos.Protos.*;

import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class StateResourceTest {
    @Mock private StateStore mockStateStore;

    private StateResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        resource = new StateResource(mockStateStore);
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
    public void testGetFrameworkIdFails() {
        when(mockStateStore.fetchFrameworkId()).thenThrow(new StateStoreException("hi"));
        Response response = resource.getFrameworkId();
        assertEquals(500, response.getStatus());
    }
}
