package com.mesosphere.sdk.offer.evaluate;

import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.testutils.TaskTestUtils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.util.Collections;

/**
 * Tests for {@link TaskPortLookup}.
 */
public class TaskPortLookupTest {

    private static final Protos.TaskInfo emptyTask = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
            .clearDiscovery()
            .clearCommand()
            .build();
    @Mock PortSpec mockPortSpec;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEmptyTask() throws Exception {
        TaskPortLookup taskPortLookup = new TaskPortLookup(emptyTask);
        when(mockPortSpec.getPortName()).thenReturn("test-port");
        when(mockPortSpec.getEnvKey()).thenReturn(null);
        assertFalse(taskPortLookup.getPriorPort(mockPortSpec).isPresent());
    }

    @Test
    public void testTaskPort() throws Exception {
        Protos.TaskInfo.Builder testTaskBuilder = emptyTask.toBuilder();
        testTaskBuilder.getDiscoveryBuilder()
                .setVisibility(Protos.DiscoveryInfo.Visibility.CLUSTER) // required by proto
                .getPortsBuilder().addPortsBuilder()
                        .setName("new-test-port")
                        .setNumber(12345);

        TaskPortLookup taskPortLookup = new TaskPortLookup(testTaskBuilder.build());
        when(mockPortSpec.getPortName()).thenReturn("new-test-port");
        assertEquals(Long.valueOf(12345), taskPortLookup.getPriorPort(mockPortSpec).get());
    }
}
