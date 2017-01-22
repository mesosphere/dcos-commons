package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Offer evaluation tests concerning volumes.
 */
public class OfferEvaluatorVolumesTest extends OfferEvaluatorBaseTest {
    @Test
    public void testCreateMultipleVolumes() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getUnreservedCpu(1);
        Resource desiredDisk = ResourceTestUtils.getUnreservedDisk(3);

        PodInstanceRequirement podInstanceRequirement = getMultiVolumePodInstanceRequirement();
        Offer offer = OfferTestUtils.getOffer(Arrays.asList(desiredCpu, desiredDisk));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(offer));
        Assert.assertEquals(6, recommendations.size());

        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(3).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(4).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(5).getOperation().getType());

        System.out.println(recommendations.get(5).getOperation());

        // Validate Create Operation
        Operation createOperation = recommendations.get(3).getOperation();
        Assert.assertEquals("pv0", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Create Operation
        createOperation = recommendations.get(4).getOperation();
        Assert.assertEquals("pv1", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Launch Operation
        Operation launchOperation = recommendations.get(5).getOperation();
        for (TaskInfo taskInfo : launchOperation.getLaunch().getTaskInfosList()) {
            for (Resource resource : taskInfo.getResourcesList()) {
                Label resourceIdLabel = getFirstLabel(resource);
                Assert.assertTrue(resourceIdLabel.getKey().equals("resource_id"));
                Assert.assertTrue(resourceIdLabel.getValue().length() > 0);
            }
        }
    }

    private PodInstanceRequirement getMultiVolumePodInstanceRequirement() throws Exception {
        PodSpec podSpec = getServiceSpec("multi-volume.yml")
                .getPods().stream().findFirst().get();

        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        List<String> taskNames = podSpec.getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        return PodInstanceRequirement.create(podInstance, taskNames);
    }

    private ServiceSpec getServiceSpec(String yamlFile) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(yamlFile).getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        return YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);
    }
}
