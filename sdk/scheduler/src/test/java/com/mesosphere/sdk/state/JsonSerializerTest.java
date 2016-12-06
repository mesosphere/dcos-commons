package com.mesosphere.sdk.state;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class JsonSerializerTest {
    private JsonSerializer serializer;

    @Before
    public void before() {
        serializer = new JsonSerializer();
    }

    @Test
    public void testSerialize() throws Exception {
        testEquals(12, Integer.class);
        testEquals("dcos dcos dcos", String.class);
        testEquals(false, Boolean.class);
    }

    @Test(expected=IOException.class)
    public void testDeserializeException() throws Exception {
        String str = "\" broken json";
        byte[] bytes = str.getBytes("UTF-8");
        serializer.deserialize(bytes, String.class);
    }

    private <T> void testEquals(T obj, Class<T> clazz) throws Exception {
        byte[] bytes = serializer.serialize(obj);
        assertEquals(obj, serializer.deserialize(bytes, clazz));
    }
}
