package org.apache.mesos.state;

import org.apache.mesos.scheduler.DefaultObservable;
import org.apache.mesos.scheduler.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Read/write interface for the state of a Scheduler.
 *
 * {@link SchedulerState} is an {@link Observable}.  It notifies its observers when tasks are modified.
 */
public class SchedulerState extends DefaultObservable {
    private static final Logger log = LoggerFactory.getLogger(SchedulerState.class);
    private static final String SUPPRESSED_KEY = "suppressed";

    private final StateStore stateStore;
    private final Serializer serializer;

    public SchedulerState(StateStore stateStore) {
        this.stateStore = stateStore;
        this.serializer = new JsonSerializer();
    }

    public boolean isSuppressed() {
        byte[] bytes = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, SUPPRESSED_KEY);

        boolean suppressed;
        try {
            suppressed = serializer.deserialize(bytes, Boolean.class);
        } catch (IOException e){
            log.error("Error converting property " + SUPPRESSED_KEY + " to boolean.", e);
            return false;
        }

        return suppressed;
    }

    public void setSuppressed(boolean isSuppressed) {
        byte[] bytes;
        try {
            bytes = serializer.serialize(isSuppressed);
        } catch (IOException e) {
            log.error("Error serializing property " + SUPPRESSED_KEY + ": " + isSuppressed + ".", e);
            return;
        }
        stateStore.storeProperty(SUPPRESSED_KEY, bytes);
    }

    public StateStore getStateStore() {
        return stateStore;
    }
}

