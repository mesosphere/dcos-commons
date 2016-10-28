package org.apache.mesos.state;

import org.apache.mesos.config.SerializationUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of {@link Serializer} for JSON input/output.
 */
public class JsonSerializer implements Serializer {
    private static final Charset DEFAULT_CHAR_SET = StandardCharsets.UTF_8;
    private final Charset charset;

    public JsonSerializer() {
        this(DEFAULT_CHAR_SET);
    }

    public JsonSerializer(Charset charset) {
        this.charset = charset;
    }

    @Override
    public <T> byte[] serialize(T value) {
        try {
            return SerializationUtils.toJsonString(value).getBytes(charset);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        return SerializationUtils.fromJsonString(new String(bytes, charset), clazz);
    }
}
