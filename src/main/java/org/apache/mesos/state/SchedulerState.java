package org.apache.mesos.state;

import org.apache.mesos.config.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Read/write interface for the state of a Scheduler.
 */
public class SchedulerState {
    private static final Logger log = LoggerFactory.getLogger(SchedulerState.class);
    private static final String SUPPRESSED_KEY = "suppressed";
    private static final Charset CHAR_SET = Charset.forName("UTF-8");

    private final StateStore stateStore;

    public SchedulerState(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public boolean isSuppressed() {
        byte[] bytes = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, SUPPRESSED_KEY);
        String json = new String(bytes, CHAR_SET);

        boolean suppressed;
        try {
            suppressed = JsonUtils.fromJsonString(json, Boolean.class);
        } catch (IOException e){
            log.error("Error converting property " + SUPPRESSED_KEY + " to boolean.", e);
            return false;
        }

        return suppressed;
    }

    public void setSuppressed(boolean isSuppressed) {
        String jsonString = JsonUtils.toJsonString(isSuppressed);
        //final byte[] suppressed = new byte[]{(byte) (isSuppressed ? 1 : 0)};
        stateStore.storeProperty(SUPPRESSED_KEY, jsonString.getBytes(CHAR_SET));
    }

    public StateStore getStateStore() {
        return stateStore;
    }
}

