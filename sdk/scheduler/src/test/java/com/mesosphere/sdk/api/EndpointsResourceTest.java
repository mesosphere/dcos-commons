package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferTestUtils;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class EndpointsResourceTest {

    private static final String CUSTOM_KEY = "custom";
    private static final TaskInfo TASK_EMPTY = TaskTestUtils.getTaskInfo(Collections.emptyList());
    private static final TaskInfo TASK_WITH_METADATA;
    private static final TaskInfo TASK_WITH_PORTS_1;
    private static final TaskInfo TASK_WITH_PORTS_2;
    private static final TaskInfo TASK_WITH_HIDDEN_DISCOVERY;
    private static final TaskInfo TASK_WITH_VIPS_1;
    private static final TaskInfo TASK_WITH_VIPS_2;
    private static final String EXPECTED_DNS_TLD = "." + Constants.DNS_TLD;
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

    @Mock private StateStore mockStateStore;

    private EndpointsResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        for (TaskInfo taskInfo : TASK_INFOS) {
            when(mockStateStore.fetchStatus(taskInfo.getName())).thenReturn(Optional.empty());
        }
        resource = buildResource(mockStateStore, "svc-name");
    }

    private static EndpointsResource buildResource(StateStore stateStore, String serviceName) {
        EndpointsResource resource = new EndpointsResource(stateStore, serviceName);
        resource.setCustomEndpoint(CUSTOM_KEY, EndpointProducer.constant(CUSTOM_VALUE));
        return resource;
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private void testEndpoint(String expectedHostname) throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoint("porta");
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(json.toString(), 3, json.length());
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", json.get("vip"));
        JSONArray dns = json.getJSONArray("dns");
        assertEquals(4, dns.length());
        assertEquals(String.format("ports-1.svc-name%s:1234", EXPECTED_DNS_TLD), dns.get(0));
        assertEquals(String.format("ports-2.svc-name%s:1243", EXPECTED_DNS_TLD), dns.get(1));
        assertEquals(String.format("vips-1.svc-name%s:2345", EXPECTED_DNS_TLD), dns.get(2));
        assertEquals(String.format("vips-2.svc-name%s:3456", EXPECTED_DNS_TLD), dns.get(3));
        JSONArray address = json.getJSONArray("address");
        assertEquals(4, address.length());
        assertEquals(address.toString(), expectedHostname + ":1234", address.get(0));
        assertEquals(expectedHostname + ":1243", address.get(1));
        assertEquals(expectedHostname + ":2345", address.get(2));
        assertEquals(expectedHostname + ":3456", address.get(3));
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
        resource = buildResource(mockStateStore, serviceName);
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoints();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(json.toString(), 4, json.length());

        assertEquals(CUSTOM_KEY, json.get(0));
        assertEquals("novip", json.get(1));
        assertEquals("porta", json.get(2));
        assertEquals("portb", json.get(3));

        assertEquals(CUSTOM_VALUE, resource.getEndpoint(CUSTOM_KEY).getEntity());

        // 'novip' port is listed across the two 'vips-' tasks
        JSONObject endpointNoVip = new JSONObject((String) resource.getEndpoint("novip").getEntity());
        assertEquals(2, endpointNoVip.length());
        JSONArray dns = endpointNoVip.getJSONArray("dns");
        assertEquals(2, dns.length());
        assertEquals("vips-1." + serviceNetworkName + EXPECTED_DNS_TLD +":2348", dns.get(0));
        assertEquals("vips-2." + serviceNetworkName + EXPECTED_DNS_TLD + ":3459", dns.get(1));
        JSONArray address = endpointNoVip.getJSONArray("address");
        assertEquals(2, address.length());
        assertEquals(TestConstants.HOSTNAME + ":2348", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3459", address.get(1));

        // 'porta' is listed across the two 'ports-' tasks and the two 'vips-' tasks
        JSONObject endpointPortA = new JSONObject((String) resource.getEndpoint("porta").getEntity());
        assertEquals(3, endpointPortA.length());
        assertEquals("vip1." + serviceNetworkName + ".l4lb.thisdcos.directory:5432", endpointPortA.get("vip"));
        dns = endpointPortA.getJSONArray("dns");
        assertEquals(4, dns.length());
        assertEquals("ports-1." + serviceNetworkName + EXPECTED_DNS_TLD + ":1234", dns.get(0));
        assertEquals("ports-2." + serviceNetworkName + EXPECTED_DNS_TLD + ":1243", dns.get(1));
        assertEquals("vips-1." + serviceNetworkName + EXPECTED_DNS_TLD + ":2345", dns.get(2));
        assertEquals("vips-2." + serviceNetworkName + EXPECTED_DNS_TLD + ":3456", dns.get(3));
        address = endpointPortA.getJSONArray("address");
        assertEquals(4, address.length());
        assertEquals(TestConstants.HOSTNAME + ":1234", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":1243", address.get(1));
        assertEquals(TestConstants.HOSTNAME + ":2345", address.get(2));
        assertEquals(TestConstants.HOSTNAME + ":3456", address.get(3));

        // 'portb' is just listed in the 'ports-1' and 'vips-2' tasks
        JSONObject endpointPortB = new JSONObject((String) resource.getEndpoint("portb").getEntity());
        assertEquals(3, endpointPortB.length());
        dns = endpointPortB.getJSONArray("dns");
        assertEquals(2, dns.length());
        assertEquals("ports-1." + serviceNetworkName + EXPECTED_DNS_TLD + ":1235", dns.get(0));
        assertEquals("vips-2." + serviceNetworkName + EXPECTED_DNS_TLD + ":3457", dns.get(1));
        address = endpointPortB.getJSONArray("address");
        assertEquals(2, address.length());
        assertEquals(TestConstants.HOSTNAME + ":1235", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3457", address.get(1));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testGetOneEndpoint() throws ConfigStoreException {
        testEndpoint(TestConstants.HOSTNAME);
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
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
        Response response = resource.getEndpoint(CUSTOM_KEY);
        assertEquals(200, response.getStatus());
        assertEquals(CUSTOM_VALUE, response.getEntity());
    }
}
