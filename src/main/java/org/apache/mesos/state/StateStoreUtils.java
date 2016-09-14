package org.apache.mesos.state;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for implementations and users of {@link StateStore}.
 */
public class StateStoreUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateStoreUtils.class);
    private static final int MAX_VALUE_LENGTH_BYTES = 1024 * 1024; // 1MB
    private static final String SUPPRESSED_KEY = "suppressed";

    private StateStoreUtils() {
        // do not instantiate
    }


    // Utilities for StateStore users:


    /**
     * Returns the requested property, or an empty array if the property is not present.
     */
    public static byte[] fetchPropertyOrEmptyArray(StateStore stateStore, String key) {
        if (stateStore.fetchPropertyKeys().contains(key)) {
            return stateStore.fetchProperty(key);
        } else {
            return new byte[0];
        }
    }

    public static void setSuppressed(StateStore stateStore, boolean isSuppressed) {
        final byte[] suppressed = new byte[]{(byte) (isSuppressed ? 1 : 0)};
        stateStore.storeProperty(SUPPRESSED_KEY, suppressed);
    }

    public static boolean isSuppressed(StateStore stateStore) {
        final byte[] suppressed = fetchPropertyOrEmptyArray(stateStore, SUPPRESSED_KEY);
        if (suppressed.length == 0) {
            return false;
        }

        if (suppressed.length != 1) {
            LOGGER.error("Unexpected SUPPRESSED byte array length: " + suppressed.length);
            return false;
        }

        return (suppressed[0] == 1) ? true : false;
    }


    // Utilities for StateStore implementations:


    /**
     * Shared implementation for validating property key limits, for use by all StateStore
     * implementations.
     *
     * @see StateStore#storeProperty(String, byte[])
     * @see StateStore#fetchProperty(String)
     */
    public static void validateKey(String key) throws StateStoreException {
        if (StringUtils.isBlank(key)) {
            throw new StateStoreException("Key cannot be blank or null");
        }
        if (key.contains("/")) {
            throw new StateStoreException("Key cannot contain '/'");
        }
    }

    /**
     * Shared implementation for validating property value limits, for use by all StateStore
     * implementations.
     *
     * @see StateStore#storeProperty(String, byte[])
     */
    public static void validateValue(byte[] value) throws StateStoreException {
        if (value == null) {
            throw new StateStoreException("Property value must not be null.");
        }
        if (value.length > MAX_VALUE_LENGTH_BYTES) {
            throw new StateStoreException(String.format(
                    "Property value length %d exceeds limit of %d bytes.",
                    value.length, MAX_VALUE_LENGTH_BYTES));
        }
    }
}
