package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

/**
 * This class tests DynamicPortRequirements.
 */
public class DynamicPortRequirementTest {

    @Test
    public void testDynamicPortRequirementConstruction()  {

        ResourceRequirement dynamicPortRequirement1 = ResourceUtils.getDesiredDynamicPort(
                TestConstants.ROLE, TestConstants.PRINCIPAL,
                Optional.of(TestConstants.PORT_NAME));

        dynamicPortRequirement1=ResourceRequirement.setVIPLabel(dynamicPortRequirement1,
                Protos.Label.newBuilder().setKey("some_key").setValue("some_value").build());

        Assert.assertNotNull(dynamicPortRequirement1);
        Boolean flag=dynamicPortRequirement1.getResource().getReservation().getLabels().getLabelsList().contains(
                Protos.Label.newBuilder().setKey(ResourceRequirement.VIP_KEY).setValue("some_key").build());

        Assert.assertTrue(flag);
    }
}
