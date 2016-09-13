package org.apache.mesos.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Abstract StateStore implementation.
 */
public abstract class AbstractStateStore implements StateStore {
    private static final Logger log = LoggerFactory.getLogger(SchedulerState.class);

    public void storePropertyAsObj(final String key, final Object obj) throws StateStoreException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.close();
            bos.close();
        } catch (IOException e) {
            log.error(String.format("Failed to write object (%s) to the StateStore", obj), e);
            return;
        }
        byte[] value = bos.toByteArray();
        storeProperty(key, value);
    }

    public Object fetchPropertyAsObj(final String key) {
        byte[] value = fetchProperty(key);
        ByteArrayInputStream bis = new ByteArrayInputStream(value);
        ObjectInput in = null;
        try {
            in = new ObjectInputStream(bis);
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error(String.format("Failed to read 'suppressed' property from the StateStore"), e);
            return false;
        }
    }
}
