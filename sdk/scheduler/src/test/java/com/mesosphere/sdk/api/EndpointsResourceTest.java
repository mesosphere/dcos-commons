package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.offer.CommonTaskUtils;
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
        CommonTaskUtils.setHostname(builder, OfferTestUtils.getOffer(Collections.emptyList()));
        CommonTaskUtils.setType(builder, "some-task-type");
        TASK_WITH_METADATA = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("with-ports-1");
        Ports.Builder portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .setName("ports-1")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder().setNumber(1234).setProtocol("tcp");
        portsBuilder.addPortsBuilder().setNumber(1235).setProtocol("tcp");
        TASK_WITH_PORTS_1 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("with-ports-2");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .setName("ports-2")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder().setNumber(1243).setProtocol("tcp");
        portsBuilder.addPortsBuilder().setNumber(1244).setProtocol("tcp");
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
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .setName("vips-1")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setNumber(2345)
                .setProtocol("tcp")
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_abc").setValue("vip1:5432");
        portsBuilder.addPortsBuilder()
                .setNumber(2346)
                .setProtocol("tcp")
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_def").setValue("vip2:6432");
        // overridden by 'custom' endpoint added below:
        portsBuilder.addPortsBuilder()
                .setNumber(2347)
                .setProtocol("tcp")
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_ghi").setValue("custom:6432");
        // VIP ignored (filed against task type instead):
        portsBuilder.addPortsBuilder()
                .setNumber(2348)
                .setProtocol("tcp")
                .getLabelsBuilder().addLabelsBuilder().setKey("ignored_no_vip").setValue("ignored:6432");
        TASK_WITH_VIPS_1 = builder.build();

        builder = TASK_WITH_METADATA.toBuilder().setName("with-vips-2");
        portsBuilder = builder.getDiscoveryBuilder()
                .setVisibility(DiscoveryInfo.Visibility.EXTERNAL)
                .setName("vips-2")
                .getPortsBuilder();
        portsBuilder.addPortsBuilder()
                .setNumber(3456)
                .setProtocol("tcp")
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_abc").setValue("vip1:5432");
        portsBuilder.addPortsBuilder()
                .setNumber(3457)
                .setProtocol("tcp")
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_def").setValue("vip2:6432");
        // overridden by 'custom' endpoint added below:
        portsBuilder.addPortsBuilder()
                .setNumber(3458)
                .setProtocol("tcp")
                .getLabelsBuilder().addLabelsBuilder().setKey("VIP_ghi").setValue("custom:6432");
        // VIP ignored (filed against task type instead):
        portsBuilder.addPortsBuilder()
                .setNumber(3459)
                .setProtocol("tcp")
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
        assertEquals(2, vip1.length());
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", vip1.get("vip"));
        JSONArray direct = vip1.getJSONArray("direct");
        assertEquals(2, direct.length());
        assertEquals("with-vips-1.svc-name.mesos:2345", direct.get(0));
        assertEquals("with-vips-2.svc-name.mesos:3456", direct.get(1));

        JSONObject vip2 = new JSONObject((String) resource.getEndpoint("vip2", null).getEntity());
        assertEquals(2, vip2.length());
        assertEquals("vip2.svc-name.l4lb.thisdcos.directory:6432", vip2.get("vip"));
        direct = vip2.getJSONArray("direct");
        assertEquals(2, direct.length());
        assertEquals("with-vips-1.svc-name.mesos:2346", direct.get(0));
        assertEquals("with-vips-2.svc-name.mesos:3457", direct.get(1));

        JSONObject taskType = new JSONObject((String) resource.getEndpoint("some-task-type", null).getEntity());
        assertEquals(1, taskType.length());
        direct = taskType.getJSONArray("direct");
        assertEquals(6, direct.length());
        assertEquals("with-ports-1.svc-name.mesos:1234", direct.get(0));
        assertEquals("with-ports-1.svc-name.mesos:1235", direct.get(1));
        assertEquals("with-ports-2.svc-name.mesos:1243", direct.get(2));
        assertEquals("with-ports-2.svc-name.mesos:1244", direct.get(3));
        assertEquals("with-vips-1.svc-name.mesos:2348", direct.get(4));
        assertEquals("with-vips-2.svc-name.mesos:3459", direct.get(5));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testGetAllEndpointsNative() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoints("native");
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(json.toString(), 4, json.length());

        assertEquals(CUSTOM_KEY, json.get(0));
        assertEquals("vip2", json.get(1));
        assertEquals("vip1", json.get(2));
        assertEquals("some-task-type", json.get(3));

        JSONObject vip1 = new JSONObject((String) resource.getEndpoint("vip1", "native").getEntity());
        assertEquals(2, vip1.length());
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", vip1.get("vip"));
        JSONArray direct = vip1.getJSONArray("direct");
        assertEquals(2, direct.length());
        assertEquals(TestConstants.HOSTNAME + ":2345", direct.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3456", direct.get(1));

        JSONObject vip2 = new JSONObject((String) resource.getEndpoint("vip2", "native").getEntity());
        assertEquals(2, vip2.length());
        assertEquals("vip2.svc-name.l4lb.thisdcos.directory:6432", vip2.get("vip"));
        direct = vip2.getJSONArray("direct");
        assertEquals(2, direct.length());
        assertEquals(TestConstants.HOSTNAME + ":2346", direct.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3457", direct.get(1));

        JSONObject taskType = new JSONObject((String) resource.getEndpoint("some-task-type", "native").getEntity());
        assertEquals(1, taskType.length());
        direct = taskType.getJSONArray("direct");
        assertEquals(6, direct.length());
        assertEquals(TestConstants.HOSTNAME + ":1234", direct.get(0));
        assertEquals(TestConstants.HOSTNAME + ":1235", direct.get(1));
        assertEquals(TestConstants.HOSTNAME + ":1243", direct.get(2));
        assertEquals(TestConstants.HOSTNAME + ":1244", direct.get(3));
        assertEquals(TestConstants.HOSTNAME + ":2348", direct.get(4));
        assertEquals(TestConstants.HOSTNAME + ":3459", direct.get(5));
    }

    @Test
    public void testGetOneEndpoint() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoint("vip1", null);
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(json.toString(), 2, json.length());
        assertEquals("vip1.svc-name.l4lb.thisdcos.directory:5432", json.get("vip"));
        JSONArray direct = json.getJSONArray("direct");
        assertEquals(2, direct.length());
        assertEquals("with-vips-1.svc-name.mesos:2345", direct.get(0));
        assertEquals("with-vips-2.svc-name.mesos:3456", direct.get(1));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testGetOneEndpointNative() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoint("vip2", "native");
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(json.toString(), 2, json.length());
        assertEquals("vip2.svc-name.l4lb.thisdcos.directory:6432", json.get("vip"));
        JSONArray direct = json.getJSONArray("direct");
        assertEquals(2, direct.length());
        assertEquals(TestConstants.HOSTNAME + ":2346", direct.get(0));
        assertEquals(TestConstants.HOSTNAME + ":3457", direct.get(1));
    }

    @Test
    public void testGetOneCustomEndpoint() throws ConfigStoreException {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getEndpoint("custom", "native");
        assertEquals(200, response.getStatus());
        assertEquals(CUSTOM_VALUE, (String) response.getEntity());
    }
}
