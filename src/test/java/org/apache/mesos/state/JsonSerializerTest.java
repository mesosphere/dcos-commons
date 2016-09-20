package org.apache.mesos.state;

import org.junit.Before;
import org.junit.Test;

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

    private <T> void testEquals(T obj, Class<T> clazz) throws Exception {
        byte[] bytes = serializer.serialize(obj);
        assertEquals(obj, serializer.deserialize(bytes, clazz));
    }
}
