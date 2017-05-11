package com.mesosphere.sdk.state;

import org.junit.Before;
import org.junit.Test;

import com.mesosphere.sdk.storage.MemPersister;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SchedulerStateTest {

    private SchedulerState schedulerState;

    @Before
    public void beforeEach() {
        schedulerState = new SchedulerState(new DefaultStateStore(new MemPersister()));
    }

    @Test
    public void testIsSuppressed() {
        assertFalse(schedulerState.isSuppressed());
        schedulerState.setSuppressed(true);
        assertTrue(schedulerState.isSuppressed());
        schedulerState.setSuppressed(false);
        assertFalse(schedulerState.isSuppressed());
    }
}
