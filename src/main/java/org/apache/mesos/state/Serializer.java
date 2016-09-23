package org.apache.mesos.state;

import java.io.IOException;

/**
 * Interfaace for serializing and deserializing objects to byte arrays using some serialization
 * format.
 */
public interface Serializer {
    public <T> byte[] serialize(T value) throws IOException;
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException;
}
