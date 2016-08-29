package org.apache.mesos.config.api;

import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigurationFactory;
import org.apache.mesos.config.StringConfiguration;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class ConfigResourceTest {

    private static final ConfigurationFactory<StringConfiguration> FACTORY =
            new StringConfiguration.Factory();

    private static final UUID ID1 = UUID.randomUUID();
    private static final UUID ID2 = UUID.randomUUID();
    private static final Map<String, String> configuration = new HashMap<>();
    private StringConfiguration CONFIG1;

    @Mock private ConfigStore<StringConfiguration> mockConfigStore;

    private ConfigResource<StringConfiguration> resource;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        resource = new ConfigResource<>(mockConfigStore, FACTORY);
        configuration.put("key", "value");
        CONFIG1 = new StringConfiguration(configuration) ;
    }

    @Test
    public void testGetConfigIds() throws ConfigStoreException {
        when(mockConfigStore.list()).thenReturn(Arrays.asList(ID1, ID2));
        Response response = resource.getConfigurationIds();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(2, json.length());
        assertEquals(ID1.toString(), json.get(0));
        assertEquals(ID2.toString(), json.get(1));
    }

    @Test
    public void testGetConfigIdsFails() throws ConfigStoreException {
        when(mockConfigStore.list()).thenThrow(new ConfigStoreException("hello"));
        Response response = resource.getConfigurationIds();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetConfig() throws ConfigStoreException {
        when(mockConfigStore.fetch(ID1, FACTORY)).thenReturn(CONFIG1);
        Response response = resource.getConfiguration(ID1.toString());
        assertEquals(200, response.getStatus());
        assertEquals(CONFIG1.toJsonString(), (String) response.getEntity());
    }

    @Test
    public void testGetConfigFails() throws ConfigStoreException {
        when(mockConfigStore.fetch(ID1, FACTORY)).thenThrow(new ConfigStoreException("hi"));
        Response response = resource.getConfiguration(ID1.toString());
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetConfigBadId() throws ConfigStoreException {
        Response response = resource.getConfiguration("hello");
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTargetId() throws ConfigStoreException {
        when(mockConfigStore.getTargetConfig()).thenReturn(ID2);
        Response response = resource.getTargetId();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(1, json.length());
        assertEquals(ID2.toString(), json.get(0));
    }

    @Test
    public void testGetTargetIdFails() throws ConfigStoreException {
        when(mockConfigStore.getTargetConfig()).thenThrow(new ConfigStoreException("hi"));
        Response response = resource.getTargetId();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTarget() throws ConfigStoreException {
        when(mockConfigStore.getTargetConfig()).thenReturn(ID2);
        when(mockConfigStore.fetch(ID2, FACTORY)).thenReturn(CONFIG1);
        Response response = resource.getTarget();
        assertEquals(200, response.getStatus());
        assertEquals(CONFIG1.toJsonString(), (String) response.getEntity());
    }

    @Test
    public void testGetTargetFailsTargetId() throws ConfigStoreException {
        when(mockConfigStore.getTargetConfig()).thenThrow(new ConfigStoreException("hi"));
        Response response = resource.getTarget();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTargetFailsConfig() throws ConfigStoreException {
        when(mockConfigStore.getTargetConfig()).thenReturn(ID2);
        when(mockConfigStore.fetch(ID2, FACTORY)).thenThrow(new ConfigStoreException("hi"));
        Response response = resource.getTarget();
        assertEquals(500, response.getStatus());
    }
}
