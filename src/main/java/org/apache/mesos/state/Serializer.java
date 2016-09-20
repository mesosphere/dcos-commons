package org.apache.mesos.state;

import java.io.IOException;

/** Serialize and deserialize objects to byte arrays */
public interface Serializer {
    public <T> byte[] serialize(T value) throws IOException;
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException;
}
