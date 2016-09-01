package org.apache.mesos.state.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.state.StateStoreException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;

/**
 * Decodes a byte array into a java object, then encodes it as JSON.
 */
public class ObjectPropertyDeserializer implements PropertyDeserializer {
    private static final Log log = LogFactory.getLog(ObjectPropertyDeserializer.class);

    @Override
    public String toJsonString(String key, byte[] value) throws StateStoreException {
        ByteArrayInputStream bis = new ByteArrayInputStream(value);
        ObjectInput in = null;

        try {
            in = new ObjectInputStream(bis);
            Object obj = in.readObject();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to convert byte array to JSON String:", e);
            return null;
        }
    }
}
