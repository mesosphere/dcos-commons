package org.apache.mesos.state.api;

import org.apache.mesos.config.SerializationUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonPropertyDeserializerTest {
    private JsonPropertyDeserializer deserializer;

    @Before
    public void before() {
        deserializer = new JsonPropertyDeserializer();
    }

    @Test
    public void testToJsonString() throws Exception {
        boolean b = false;
        byte[] barr = SerializationUtils.toJsonString(b).getBytes(StandardCharsets.UTF_8);
        String bjson = deserializer.toJsonString("key", barr);
        assertEquals(bjson, "false");
    }
}
