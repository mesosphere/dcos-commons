package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * This class tests DynamicPortRequirements.
 */
public class DynamicPortRequirementTest {

    @Test
    public void testDynamicPortRequirementConstruction()  {
        Protos.Value.Range range = Protos.Value.Range.newBuilder().setBegin(0).setEnd(0).build();
        Protos.Value.Range range2 = Protos.Value.Range.newBuilder().setBegin(1003).setEnd(1003).build();
        Protos.Value.Range range3 = Protos.Value.Range.newBuilder().setBegin(2003).setEnd(2003).build();

        Protos.Resource desiredPort = ResourceUtils.getDesiredRanges(TestConstants.ROLE, TestConstants.PRINCIPAL,
                "ports", Arrays.asList(range));

        Protos.Resource desiredPort2 = ResourceUtils.getDesiredRanges(TestConstants.ROLE, TestConstants.PRINCIPAL,
                "ports", Arrays.asList(range2));

        Protos.Resource desiredPort3 = ResourceUtils.getDesiredRanges(TestConstants.ROLE, TestConstants.PRINCIPAL,
                "ports", Arrays.asList(range3));

        ResourceRequirement dynamicPortRequirement1 = new ResourceRequirement(desiredPort);
        dynamicPortRequirement1.setEnvName(TestConstants.PORT_NAME);

        dynamicPortRequirement1.setVIPLabel( Protos.Label.newBuilder().setKey("some_key").setValue("some_value").build());

        Assert.assertNotNull(dynamicPortRequirement1);
        Boolean flag=dynamicPortRequirement1.getResource().getReservation().getLabels().getLabelsList().contains(
                Protos.Label.newBuilder().setKey(ResourceRequirement.VIP_KEY).setValue("some_key").build());

        Assert.assertEquals(true, flag);
    }
}
