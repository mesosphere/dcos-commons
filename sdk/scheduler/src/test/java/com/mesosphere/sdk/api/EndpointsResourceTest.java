package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class EndpointsResourceTest {

    private static final TaskInfo TASK_EMPTY = TaskTestUtils.getTaskInfo(Collections.emptyList());
    private static final TaskInfo TASK_WITH_METADATA;
    private static final TaskInfo TASK_WITH_PORTS_1;
    private static final TaskInfo TASK_WITH_PORTS_2;
    private static final TaskInfo TASK_WITH_HIDDEN_DISCOVERY;
    private static final TaskInfo TASK_WITH_VIPS_1;
    private static final TaskInfo TASK_WITH_VIPS_2;
    static {
        TaskInfo.Builder builder = TASK_EMPTY.toBuilder();
        builder.setLabels(new SchedulerLabelWriter(builder)
                .setHostname(OfferTestUtils.getOffer(Collections.emptyList()))
                .setType("some-task-type")
                .toProto());
        TASK_WITH_METADATA = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("with-ports-1");
        Ports.Builder portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .setName("ports-1")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setNumber(1234)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        portsBuilder.addPortsBuilder()
                .setNumber(1235)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        TASK_WITH_PORTS_1 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("with-ports-2");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setNumber(1243)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        portsBuilder.addPortsBuilder()
                .setNumber(1244)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL);
        TASK_WITH_PORTS_2 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("hidden-discovery");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .setName("hidden")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder().setNumber(1).setProtocol("tcp");
        portsBuilder.addPortsBuilder().setNumber(2).setProtocol("tcp");
        TASK_WITH_HIDDEN_DISCOVERY = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("with-vips-1");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .setName("vips-1")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setNumber(2345)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_abc").setValue("vip1:5432");
        portsBuilder.addPortsBuilder()
                .setNumber(2346)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_def").setValue("vip2:6432");
        // overridden by 'custom' endpoint added below:
        portsBuilder.addPortsBuilder()
                .setNumber(2347)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_ghi").setValue("custom:6432");
        // VIP ignored (filed against task type instead):
        portsBuilder.addPortsBuilder()
                .setNumber(2348)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("ignored_no_vip").setValue("ignored:6432");
        TASK_WITH_VIPS_1 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("with-vips-2");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.CLUSTER)
                .setName("vips-2")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setNumber(3456)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_abc").setValue("vip1:5432");
        portsBuilder.addPortsBuilder()
                .setNumber(3457)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_def").setValue("vip2:6432");
        // overridden by 'custom' endpoint added below:
        portsBuilder.addPortsBuilder()
                .setNumber(3458)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_ghi").setValue("custom:6432");
        // VIP ignored (filed against task type instead):
        portsBuilder.addPortsBuilder()
                .setNumber(3459)
                .setProtocol("tcp")
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .getLabelsBuilder().addLabelsBuilder().setKey("ignored_no_vip").setValue("ignored:6432");
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
    private static final String CUSTOM_KEY = "custom";
    private static final String CUSTOM_VALUE = "hi\nhey\nhello";

    @Mock private StateStore mockStateStore;

    private EndpointsResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        resource = new EndpointsResource(mockStateStore, "svc-name");
        resource.setCustomEndpoint(CUSTOM_KEY, EndpointProducer.constant(CUSTOM_VALUE));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testGetAllEndpoints() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoints(null);
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(json.toString(), 4, json.length());

        assertEquals(CUSTOM_KEY, json.get(0));
        assertEquals("vip2", json.get(1));
        assertEquals("vip1", json.get(2));
        assertEquals("some-task-type", json.get(3));

        JSONObject vip1 = new JSONObject((String) resource.getEndpoint("vip1", null).getEntity());
        // due to deprecated "vip", decremented expected at 1.9 -> 2.0
        assertEquals(4, vip1.length());
        // deprecated, remove "vip" at 1.9 -> 2.0
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", vip1.get("vip"));
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", vip1.getJSONArray("vips").get(0));
        JSONArray dns = vip1.getJSONArray("dns");
        assertEquals(2, dns.length());
        assertEquals("vips-1.svc-name.mesos:2345", dns.get(0));
        assertEquals("vips-2.svc-name.mesos:3456", dns.get(1));
        JSONArray address = vip1.getJSONArray("address");
        assertEquals(2, address.length());
        assertEquals(TestConstants.HOSTNAME + ":2345", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3456", address.get(1));

        JSONObject vip2 = new JSONObject((String) resource.getEndpoint("vip2", null).getEntity());
        // due to deprecated "vip", decremented expected at 1.9 -> 2.0
        assertEquals(4, vip2.length());
        // deprecated, remove "vip" at 1.9 -> 2.0
        assertEquals("vip2.svc-name.l4lb.thisdcos.directory:6432", vip2.get("vip"));
        assertEquals("vip2.svc-name.l4lb.thisdcos.directory:6432", vip2.getJSONArray("vips").get(0));
        dns = vip2.getJSONArray("dns");
        assertEquals(2, dns.length());
        assertEquals("vips-1.svc-name.mesos:2346", dns.get(0));
        assertEquals("vips-2.svc-name.mesos:3457", dns.get(1));
        address = vip2.getJSONArray("address");
        assertEquals(2, address.length());
        assertEquals(TestConstants.HOSTNAME + ":2346", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3457", address.get(1));

        JSONObject taskType = new JSONObject((String) resource.getEndpoint("some-task-type", null).getEntity());
        assertEquals(2, taskType.length());
        dns = taskType.getJSONArray("dns");
        assertEquals(6, dns.length());
        assertEquals("ports-1.svc-name.mesos:1234", dns.get(0));
        assertEquals("ports-1.svc-name.mesos:1235", dns.get(1));
        // This task's DiscoveryInfo doesn't have a name set, so it should use the task name for its Mesos-DNS prefix.
        assertEquals("with-ports-2.svc-name.mesos:1243", dns.get(2));
        assertEquals("with-ports-2.svc-name.mesos:1244", dns.get(3));
        assertEquals("vips-1.svc-name.mesos:2348", dns.get(4));
        assertEquals("vips-2.svc-name.mesos:3459", dns.get(5));
        address = taskType.getJSONArray("address");
        assertEquals(6, address.length());
        assertEquals(TestConstants.HOSTNAME + ":1234", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":1235", address.get(1));
        assertEquals(TestConstants.HOSTNAME + ":1243", address.get(2));
        assertEquals(TestConstants.HOSTNAME + ":1244", address.get(3));
        assertEquals(TestConstants.HOSTNAME + ":2348", address.get(4));
        assertEquals(TestConstants.HOSTNAME + ":3459", address.get(5));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testGetOneEndpoint() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoint("vip1", null);
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        // due to deprecated "vip", decremented expected at 1.9 -> 2.0
        assertEquals(json.toString(), 4, json.length());
        // deprecated, remove "vip" at 1.9 -> 2.0
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", json.get("vip"));
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", json.getJSONArray("vips").get(0));
        JSONArray dns = json.getJSONArray("dns");
        assertEquals(2, dns.length());
        assertEquals("vips-1.svc-name.mesos:2345", dns.get(0));
        assertEquals("vips-2.svc-name.mesos:3456", dns.get(1));
        JSONArray address = json.getJSONArray("address");
        assertEquals(2, address.length());
        assertEquals(TestConstants.HOSTNAME + ":2345", address.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3456", address.get(1));
    }

    @Test
    public void testGetOneCustomEndpoint() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoint("custom", null);
        assertEquals(200, response.getStatus());
        assertEquals(CUSTOM_VALUE, response.getEntity());
    }

    @Test
    public void testGetAllEndpointsNativeIgnored() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        assertEquals(resource.getEndpoints(null).getEntity(), resource.getEndpoints("native").getEntity());
        assertEquals(resource.getEndpoint("vip1", null).getEntity(),
                resource.getEndpoint("vip1", "native").getEntity());
        assertEquals(resource.getEndpoint("vip2", null).getEntity(),
                resource.getEndpoint("vip2", "native").getEntity());
        assertEquals(resource.getEndpoint("some-task-type", null).getEntity(),
                resource.getEndpoint("some-task-type", "native").getEntity());
        assertEquals(resource.getEndpoint("custom", null).getEntity(),
                resource.getEndpoint("custom", "native").getEntity());
    }
}
