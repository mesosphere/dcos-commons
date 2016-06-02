package org.apache.mesos.scheduler.plan.api;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.apache.mesos.scheduler.plan.StageManager;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.ws.rs.core.Response;

@RunWith(PowerMockRunner.class)
@PrepareForTest(StageInfo.class)
public class StageResourceTest {
    public StageManager stageManager = Mockito.mock(StageManager.class);
    @Rule
    public ResourceTestRule resources = ResourceTestRule
            .builder()
            .addResource(new StageResource(stageManager))
            .build();

    @Test
    public void testGetFullInfo() {
        PowerMockito.mockStatic(StageInfo.class);
        PowerMockito.when(StageInfo.forStage(stageManager)).thenReturn(null);

        Mockito.when(stageManager.isComplete()).thenReturn(true);
        Response response = resources.client().target("/v1/plan").request().get();
        Assert.assertEquals(200, response.getStatus());

        Mockito.when(stageManager.isComplete()).thenReturn(false);
        response = resources.client().target("/v1/plan").request().get();
        Assert.assertEquals(503, response.getStatus());
    }
}
