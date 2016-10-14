package org.apache.mesos.state;

import org.apache.commons.lang3.StringUtils;

/**
 * Utilities for implementations and users of {@link StateStore}.
 */
public class StateStoreUtils {

    private static final int MAX_VALUE_LENGTH_BYTES = 1024 * 1024; // 1MB

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
