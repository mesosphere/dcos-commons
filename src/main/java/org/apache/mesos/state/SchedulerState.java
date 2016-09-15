package org.apache.mesos.state;

import org.apache.mesos.Protos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Read/write interface for the state of a Scheduler.
 */
public class SchedulerState  {
    private static final Logger log = LoggerFactory.getLogger(SchedulerState.class);

    private static final String SUPPRESSED_KEY = "suppressed";
    private final StateStore stateStore;

    public SchedulerState(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public Optional<FrameworkID> getFrameworkId() {
        try {
            return stateStore.fetchFrameworkId();
        } catch (StateStoreException ex) {
            log.warn("Failed to get FrameworkID. "
                    + "This is expected when the service is starting for the first time.", ex);
        }
        return Optional.empty();
    }

    public void setFrameworkId(FrameworkID fwkId) throws StateStoreException {
        try {
            log.info(String.format("Storing FrameworkID: %s", fwkId));
            stateStore.storeFrameworkId(fwkId);
        } catch (StateStoreException ex) {
            log.error("Failed to set FrameworkID: " + fwkId, ex);
            throw ex;
        }
    }

    public boolean isSuppressed() {
        byte[] suppressed = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, SUPPRESSED_KEY);
        if (suppressed.length == 0) {
            return false;
        }

        if (suppressed.length != 1) {
            log.error("Unexpected SUPPRESSED byte array length: " + suppressed.length);
            return false;
        }

        return (suppressed[0] == 1) ? true : false;
    }

    public void setSuppressed(boolean isSuppressed) {
        final byte[] suppressed = new byte[]{(byte) (isSuppressed ? 1 : 0)};
        stateStore.storeProperty(SUPPRESSED_KEY, suppressed);
    }

    public StateStore getStateStore() {
        return stateStore;
    }
}

