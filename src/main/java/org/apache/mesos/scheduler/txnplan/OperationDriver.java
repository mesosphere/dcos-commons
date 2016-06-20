package org.apache.mesos.scheduler.txnplan;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A unique OperationDriver is provided to each Operation to give it common SDK functionality.
 * This includes checkpointing (via {@link OperationDriver#save(Object)} and
 * {@link OperationDriver#load()}, as well as logging via {@link OperationDriver#info(String)}
 * and friends.
 *
 * OperationDriver does not guarantee thread safety--please use responsibly, in a threadsafe way.
 *
 * Created by dgrnbrg on 6/20/16.
 */
public interface OperationDriver {
    /**
     * Saves any object, including null, to a persistent context-aware store.
     * The object must be Kryo serializable. When this returns, the object has
     * been durably persisted. This is used to checkpoint within a single operation.
     * @param o The object to save
     */
    void save(Object o);

    /**
     * Loads the most recently saved object.
     * @return The deserialized object, or, if nothing's ever been saved, returns null.
     */
    Object load();

    /**
     * Convenience logging function for operations
     * @param msg
     */
    void info(String msg);

    /**
     * Convenience logging function for operations
     * @param msg
     */
    void error(String msg);
}
