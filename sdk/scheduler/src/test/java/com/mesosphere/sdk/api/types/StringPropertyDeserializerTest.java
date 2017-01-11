package com.mesosphere.sdk.api.types;

import com.mesosphere.sdk.config.SerializationUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

/**
 * Tests for {@link StringPropertyDeserializer}.
 */
public class StringPropertyDeserializerTest {
    private StringPropertyDeserializer deserializer;

    @Before
    public void before() {
        deserializer = new StringPropertyDeserializer();
    }

    @Test
    public void testToJsonString() throws Exception {
        boolean b = false;
        byte[] barr = SerializationUtils.toJsonString(b).getBytes(StandardCharsets.UTF_8);
        String bjson = deserializer.toJsonString("key", barr);
        assertEquals(bjson, "false");
    }
}
