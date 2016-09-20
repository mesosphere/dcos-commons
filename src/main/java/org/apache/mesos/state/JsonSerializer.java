package org.apache.mesos.state;


import org.apache.mesos.config.JsonUtils;

import java.io.IOException;
import java.nio.charset.Charset;

/** JsonSerializer */
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
    public <T> byte[] serialize(T value) throws IOException {
        String json = JsonUtils.toJsonString(value);
        return json.getBytes("UTF-8");
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        String json = new String(bytes, "UTF-8");
        return JsonUtils.fromJsonString(json, clazz);
    }
}
