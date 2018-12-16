package com.mesosphere.sdk.scheduler;

import java.nio.charset.StandardCharsets;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.curator.CuratorLocker;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;

import static org.mockito.Mockito.*;

public class SchedulerRunnerTest {

    @Mock private SchedulerBuilder mockSchedulerBuilder;
    @Mock private SchedulerConfig mockSchedulerConfig;
    @Mock private ServiceSpec mockServiceSpec;

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

    @Test(expected = IllegalArgumentException.class)
    public void checkSchemaVersion() throws Exception {
        // Set up a schema version which shouldn't work, to verify that the schema version is being checked:
        Persister persister = MemPersister.newBuilder().build();
        persister.set("SchemaVersion", "123".getBytes(StandardCharsets.UTF_8));

        when(mockSchedulerBuilder.getPersister()).thenReturn(persister);
        when(mockSchedulerBuilder.getSchedulerConfig()).thenReturn(mockSchedulerConfig);
        when(mockSchedulerBuilder.getServiceSpec()).thenReturn(mockServiceSpec);
        SchedulerRunner.fromSchedulerBuilder(mockSchedulerBuilder).run();
        Assert.fail("Expected exception due to bad schema version");
    }
}
