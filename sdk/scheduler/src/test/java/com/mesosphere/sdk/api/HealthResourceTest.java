package com.mesosphere.sdk.api;

import java.util.Arrays;
import java.util.Collections;

import javax.ws.rs.core.Response;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.Plan;

import static org.mockito.Mockito.when;

public class HealthResourceTest {
    @Mock private Plan mockPlan1;
    @Mock private Plan mockPlan2;

    private HealthResource resource;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        resource = new HealthResource().setHealthyPlanManagers(Arrays.asList(
                DefaultPlanManager.createProceeding(mockPlan1),
                DefaultPlanManager.createProceeding(mockPlan2)));
    }

    @Test
    public void testPlanErrorIncomplete() {
        when(mockPlan1.getErrors()).thenReturn(Collections.singletonList("err"));
        when(mockPlan1.isComplete()).thenReturn(true);
        when(mockPlan2.getErrors()).thenReturn(Collections.emptyList());
        when(mockPlan2.isComplete()).thenReturn(false);
        checkHealthStatus(Response.Status.EXPECTATION_FAILED);
    }

    @Test
    public void testPlanError() {
        when(mockPlan1.getErrors()).thenReturn(Collections.singletonList("err"));
        when(mockPlan1.isComplete()).thenReturn(true);
        when(mockPlan2.getErrors()).thenReturn(Collections.emptyList());
        when(mockPlan2.isComplete()).thenReturn(true);
        checkHealthStatus(Response.Status.EXPECTATION_FAILED);
    }

    @Test
    public void testPlanIncomplete() {
        when(mockPlan1.getErrors()).thenReturn(Collections.emptyList());
        when(mockPlan1.isComplete()).thenReturn(false);
        when(mockPlan2.getErrors()).thenReturn(Collections.emptyList());
        when(mockPlan2.isComplete()).thenReturn(true);
        checkHealthStatus(Response.Status.ACCEPTED);
    }

    @Test
    public void testPlanComplete() {
        when(mockPlan1.getErrors()).thenReturn(Collections.emptyList());
        when(mockPlan1.isComplete()).thenReturn(true);
        when(mockPlan2.getErrors()).thenReturn(Collections.emptyList());
        when(mockPlan2.isComplete()).thenReturn(true);
        checkHealthStatus(Response.Status.OK);
    }

    private void checkHealthStatus(Response.Status status) {
        Response response = resource.getHealth();
        Assert.assertEquals(status, response.getStatusInfo());
        Assert.assertEquals(status.toString(), response.getEntity());
    }
}
