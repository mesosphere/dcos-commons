package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Ports;
import org.apache.mesos.Protos.TaskInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class EndpointsQueriesTest {

    private static final String CUSTOM_KEY = "custom";
    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();
    private static final TaskInfo TASK_EMPTY = TaskTestUtils.getTaskInfo(Collections.emptyList());
    private static final TaskInfo TASK_WITH_METADATA;
    private static final TaskInfo TASK_WITH_PORTS_1;
    private static final TaskInfo TASK_WITH_PORTS_2;
    private static final TaskInfo TASK_WITH_HIDDEN_DISCOVERY;
    private static final TaskInfo TASK_WITH_VIPS_1;
    private static final TaskInfo TASK_WITH_VIPS_2;
    static {
        TaskInfo.Builder builder = TASK_EMPTY.toBuilder();
        builder.setLabels(new TaskLabelWriter(builder)
                .setHostname(OfferTestUtils.getOffer(Collections.emptyList()))
                .setType("some-task-type")
                .toProto());
        TASK_WITH_METADATA = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("ports-1");
        Ports.Builder portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setName("porta")
                .setNumber(1234)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        portsBuilder.addPortsBuilder()
                .setName("portb")
                .setNumber(1235)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        TASK_WITH_PORTS_1 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("ports-2");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setName("porta")
                .setNumber(1243)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        portsBuilder.addPortsBuilder() // no name, ignored in output
                .setNumber(1244)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        TASK_WITH_PORTS_2 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("hidden-ports");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setName("porta-hidden")
                .setNumber(1)
                .setProtocol("tcp");
        portsBuilder.addPortsBuilder()
                .setName("portb-hidden")
                .setNumber(2)
                .setProtocol("tcp");
        TASK_WITH_HIDDEN_DISCOVERY = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("vips-1");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setName("porta")
                .setNumber(2345)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_abc").setValue("vip1:5432");
        // overridden by 'custom' endpoint added below:
        portsBuilder.addPortsBuilder()
                .setName(CUSTOM_KEY)
                .setNumber(2347)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_ghi").setValue("custom:6432");
        // VIP ignored (filed against task type instead):
        portsBuilder.addPortsBuilder()
                .setName("novip")
                .setNumber(2348)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("ignored_not_vip").setValue("ignored:6432");
        TASK_WITH_VIPS_1 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("vips-2");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setName("porta")
                .setNumber(3456)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_abc").setValue("vip1:5432");
        portsBuilder.addPortsBuilder()
                .setName("portb")
                .setNumber(3457)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_def").setValue("vip2:6432");
        // overridden by 'custom' endpoint added below:
        portsBuilder.addPortsBuilder()
                .setName(CUSTOM_KEY)
                .setNumber(3458)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_ghi").setValue("custom:6432");
        // VIP ignored (filed against task type instead):
        portsBuilder.addPortsBuilder()
                .setName("novip")
                .setNumber(3459)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("ignored_not_vip").setValue("ignored:6432");
        TASK_WITH_VIPS_2 = builder.build();
    }
    private static final Collection<TaskInfo> TASK_INFOS = Arrays.asList(
            TASK_EMPTY,
            TASK_WITH_METADATA,
            TASK_WITH_PORTS_1,
            TASK_WITH_PORTS_2,
            TASK_WITH_HIDDEN_DISCOVERY,
            TASK_WITH_VIPS_1,
            TASK_WITH_VIPS_2);
    private static final String CUSTOM_VALUE = "hi\nhey\nhello";
    private static final String SERVICE_NAME = "svc-name";
    private static final Map<String, EndpointProducer> CUSTOM_ENDPOINTS =
            Collections.singletonMap(CUSTOM_KEY, EndpointProducer.constant(CUSTOM_VALUE));

    @Mock private StateStore mockStateStore;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        for (TaskInfo taskInfo : TASK_INFOS) {
            when(mockStateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.empty());
        }
    }

    @Test
    public void testGetAllEndpoints() throws ConfigStoreException {
        allEndpointsTest("svc-name", "svc-name");
    }

    @Test
    public void testGetAllEndpointsFolderedService() throws ConfigStoreException {
        allEndpointsTest("/path/to/svc-name", "pathtosvc-name");
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private void allEndpointsTest(String serviceName, String serviceNetworkName) {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = EndpointsQueries.getEndpoints(mockStateStore, serviceName, CUSTOM_ENDPOINTS, SCHEDULER_CONFIG);
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(json.toString(), 4, json.length());

        assertEquals(CUSTOM_KEY, json.get(0));
        assertEquals("novip", json.get(1));
        assertEquals("porta", json.get(2));
        assertEquals("portb", json.get(3));

        assertEquals(CUSTOM_VALUE, EndpointsQueries.getEndpoint(mockStateStore, serviceName, CUSTOM_ENDPOINTS, CUSTOM_KEY, SCHEDULER_CONFIG).getEntity());

        // 'novip' port is listed across the two 'vips-' tasks
        JSONObject endpointNoVip = new JSONObject(
                (String) EndpointsQueries.getEndpoint(mockStateStore, serviceName, CUSTOM_ENDPOINTS, "novip", SCHEDULER_CONFIG).getEntity());
        assertEquals(2, endpointNoVip.length());
        JSONArray dns = endpointNoVip.getJSONArray("dns");
        assertEquals(2, dns.length());
        assertEquals(String.format("vips-1.%s.%s:2348", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(0));
        assertEquals(String.format("vips-2.%s.%s:3459", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(1));
        JSONArray address = endpointNoVip.getJSONArray("address");
        assertEquals(2, address.length());
        assertEquals(TestConstants.HOSTNAME + ":2348", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3459", address.get(1));

        // 'porta' is listed across the two 'ports-' tasks and the two 'vips-' tasks
        JSONObject endpointPortA = new JSONObject(
                (String) EndpointsQueries.getEndpoint(mockStateStore, serviceName, CUSTOM_ENDPOINTS, "porta", SCHEDULER_CONFIG).getEntity());
        assertEquals(3, endpointPortA.length());
        assertEquals(String.format("vip1.%s.%s:5432", serviceNetworkName, SCHEDULER_CONFIG.getVipTLD()), endpointPortA.get("vip"));
        dns = endpointPortA.getJSONArray("dns");
        assertEquals(4, dns.length());
        assertEquals(String.format("ports-1.%s.%s:1234", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(0));
        assertEquals(String.format("ports-2.%s.%s:1243", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(1));
        assertEquals(String.format("vips-1.%s.%s:2345", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(2));
        assertEquals(String.format("vips-2.%s.%s:3456", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(3));
        address = endpointPortA.getJSONArray("address");
        assertEquals(4, address.length());
        assertEquals(TestConstants.HOSTNAME + ":1234", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":1243", address.get(1));
        assertEquals(TestConstants.HOSTNAME + ":2345", address.get(2));
        assertEquals(TestConstants.HOSTNAME + ":3456", address.get(3));

        // 'portb' is just listed in the 'ports-1' and 'vips-2' tasks
        JSONObject endpointPortB = new JSONObject(
                (String) EndpointsQueries.getEndpoint(mockStateStore, serviceName, CUSTOM_ENDPOINTS, "portb", SCHEDULER_CONFIG).getEntity());
        assertEquals(3, endpointPortB.length());
        dns = endpointPortB.getJSONArray("dns");
        assertEquals(2, dns.length());
        assertEquals(String.format("ports-1.%s.%s:1235", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(0));
        assertEquals(String.format("vips-2.%s.%s:3457", serviceNetworkName, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(1));
        address = endpointPortB.getJSONArray("address");
        assertEquals(2, address.length());
        assertEquals(TestConstants.HOSTNAME + ":1235", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3457", address.get(1));
    }

    @Test
    public void testGetOneEndpoint() throws ConfigStoreException {
        testEndpoint(TestConstants.HOSTNAME);
    }

    @Test
    public void testOneOverlayEndpoint() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        testEndpoint(TestConstants.HOSTNAME);  // confirm that we default to offer hostname

        Protos.TaskStatus TASK_STATUS = createTaskStatus(TestConstants.OVERLAY_HOSTNAME);

        for (TaskInfo taskInfo : TASK_INFOS) {
            when(mockStateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.of(TASK_STATUS));
            when(mockStateStore.fetchProperty(taskInfo.getName() + ":task-status")).thenReturn(TASK_STATUS.toByteArray());
        }

        testEndpoint(TestConstants.OVERLAY_HOSTNAME);

        Protos.TaskStatus TASK_STATUS_2 = createTaskStatus("otherHost");

        for (TaskInfo taskInfo : TASK_INFOS) {
            when(mockStateStore.fetchProperty(taskInfo.getName() + ":task-status"))
                    .thenReturn(TASK_STATUS_2.toByteArray());
        }

        testEndpoint(TestConstants.OVERLAY_HOSTNAME);

        for (TaskInfo taskInfo : TASK_INFOS) {
            when(mockStateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.empty());
        }

        testEndpoint("otherHost");
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private void testEndpoint(String expectedHostname) throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = EndpointsQueries.getEndpoint(
                mockStateStore, SERVICE_NAME, CUSTOM_ENDPOINTS, "porta", SCHEDULER_CONFIG);
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(json.toString(), 3, json.length());
        assertEquals(String.format("vip1.svc-name.%s:5432", SCHEDULER_CONFIG.getVipTLD()), json.get("vip"));
        JSONArray dns = json.getJSONArray("dns");
        assertEquals(4, dns.length());
        assertEquals(String.format("ports-1.%s.%s:1234", SERVICE_NAME, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(0));
        assertEquals(String.format("ports-2.%s.%s:1243", SERVICE_NAME, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(1));
        assertEquals(String.format("vips-1.%s.%s:2345", SERVICE_NAME, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(2));
        assertEquals(String.format("vips-2.%s.%s:3456", SERVICE_NAME, SCHEDULER_CONFIG.getAutoipTLD()), dns.get(3));
        JSONArray address = json.getJSONArray("address");
        assertEquals(4, address.length());
        assertEquals(address.toString(), expectedHostname + ":1234", address.get(0));
        assertEquals(expectedHostname + ":1243", address.get(1));
        assertEquals(expectedHostname + ":2345", address.get(2));
        assertEquals(expectedHostname + ":3456", address.get(3));
    }

    private static Protos.TaskStatus createTaskStatus(String hostname) {
        Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                .setState(Protos.TaskState.TASK_RUNNING)
                .setTaskId(TestConstants.TASK_ID);
        taskStatusBuilder.getContainerStatusBuilder().addNetworkInfosBuilder().addIpAddressesBuilder()
                .setIpAddress(hostname);
        return taskStatusBuilder.build();
    }

    @Test
    public void testGetOneCustomEndpoint() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = EndpointsQueries.getEndpoint(
                mockStateStore, SERVICE_NAME, CUSTOM_ENDPOINTS, CUSTOM_KEY, SCHEDULER_CONFIG);
        assertEquals(200, response.getStatus());
        assertEquals(CUSTOM_VALUE, response.getEntity());
    }
}
