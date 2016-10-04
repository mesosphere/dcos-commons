package org.apache.mesos.state;


import org.apache.mesos.config.JsonUtils;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Implementation of {@link Serializer} for JSON input/output.
 */
public class JsonSerializer implements Serializer {
    private static final Charset DEFAULT_CHAR_SET = Charset.forName("UTF-8");
    private final Charset charset;

    public JsonSerializer() {
        this(DEFAULT_CHAR_SET);
    }

    public JsonSerializer(Charset charset) {
        this.charset = charset;
    }

    @Override
    public <T> byte[] serialize(T value) {
        String json = JsonUtils.toJsonString(value);
        return json.getBytes(charset);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        String json = new String(bytes, charset);
        return JsonUtils.fromJsonString(json, clazz);
    }
}
