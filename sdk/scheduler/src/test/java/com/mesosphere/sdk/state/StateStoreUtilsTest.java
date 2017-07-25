package com.mesosphere.sdk.state;

import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import net.jcip.annotations.ThreadSafe;
import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link StateStoreUtils}.
 */
public class StateStoreUtilsTest {

    @Test
    public void emptyStateStoreReturnsEmptyArray () {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void unmatchedKeyReturnsEmptyArray() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());
        stateStore.storeProperty("DEFINED", "VALUE".getBytes());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void matchedKeyReturnsValue() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());
        stateStore.storeProperty("DEFINED", "VALUE".getBytes());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "DEFINED");

        assertThat(result, is("VALUE".getBytes()));
    }

    @Test(expected = StateStoreException.class)
    public void emptyStringIsIsInvalidKey() {
        StateStoreUtils.validateKey("");
    }

    @Test(expected = StateStoreException.class)
    public void nullIsIsInvalidKey() {
        StateStoreUtils.validateKey(null);
    }

    @Test(expected = StateStoreException.class)
    public void blankStringIsInvaldiKey() {
        StateStoreUtils.validateKey("    ");
    }

    @Test(expected = StateStoreException.class)
    public void stringWithLeadingSlashIsInvalidKey() {
        StateStoreUtils.validateKey("/key");
    }

    @Test(expected = StateStoreException.class)
    public void stringWithTrailingSlashIsInvalidKey() {
        StateStoreUtils.validateKey("key/");
    }

    @Test(expected = StateStoreException.class)
    public void stringWithEmbeddedSlashIsInvalidKey() {
        StateStoreUtils.validateKey("key/value");
    }


}
