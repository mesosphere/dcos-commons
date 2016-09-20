package org.apache.mesos.state.api;

import org.apache.mesos.config.JsonUtils;
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
        String bstr = JsonUtils.toJsonString(b);
        byte[] barr = bstr.getBytes("UTF-8");
        String bjson = deserializer.toJsonString("key", barr);
        assertEquals(bjson, "false");
    }
}
