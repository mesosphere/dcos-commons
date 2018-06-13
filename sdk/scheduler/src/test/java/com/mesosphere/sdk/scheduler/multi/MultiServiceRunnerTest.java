package com.mesosphere.sdk.scheduler.multi;

import java.nio.charset.StandardCharsets;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.scheduler.MesosEventClient;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;

/**
 * Tests for {@link MultiServiceRunner}
 */
public class MultiServiceRunnerTest {

    @Mock SchedulerConfig mockSchedulerConfig;
    @Mock FrameworkConfig mockFrameworkConfig;
    @Mock MesosEventClient mockClient;

    @BeforeClass
    public static void beforeAll() {
        CuratorLocker.setEnabledForTests(false);
    }

    @AfterClass
    public static void afterAll() {
        CuratorLocker.setEnabledForTests(true);
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void checkSchemaVersionFails() throws Exception {
        // Set up a schema version which shouldn't work, to verify that the schema version is being checked:
        Persister persister = new MemPersister();
        persister.set("SchemaVersion", "123".getBytes(StandardCharsets.UTF_8));

        MultiServiceRunner.Builder runnerBuilder =
                MultiServiceRunner.newBuilder(mockSchedulerConfig, mockFrameworkConfig, persister, mockClient);
        try {
            runnerBuilder.build();
            Assert.fail("Expected exception due to bad schema version");
        } catch (IllegalStateException e) {
            Assert.assertEquals(
                    "Storage schema version 123 is not supported by this software (expected: 2)", e.getMessage());
        }
    }

    @Test
    public void checkSchemaVersionSucceeds() throws Exception {
        // Set up a schema version which shouldn't work, to verify that the schema version is being checked:
        Persister persister = new MemPersister();
        persister.set("SchemaVersion", "2".getBytes(StandardCharsets.UTF_8));

        MultiServiceRunner.newBuilder(mockSchedulerConfig, mockFrameworkConfig, persister, mockClient).build();
    }
}
