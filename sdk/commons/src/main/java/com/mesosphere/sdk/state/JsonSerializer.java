package com.mesosphere.sdk.state;

import com.mesosphere.sdk.config.SerializationUtils;

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
        return SerializationUtils.toJsonStringOrEmpty(value).getBytes(charset);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        return SerializationUtils.fromJsonString(new String(bytes, charset), clazz);
    }
}
