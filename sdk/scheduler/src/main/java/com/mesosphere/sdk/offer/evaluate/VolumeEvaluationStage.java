package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.ResourceRequirement;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.offer.OfferRequirement}, ensuring that it
 * contains an appropriately-sized volume, and creating any necessary instances of
 * {@link com.mesosphere.sdk.offer.ReserveOfferRecommendation},
 * {@link com.mesosphere.sdk.offer.UnreserveOfferRecommendation}, and
 * {@link com.mesosphere.sdk.offer.CreateOfferRecommendation} as necessary.
 */
public class VolumeEvaluationStage extends ResourceEvaluationStage implements OfferEvaluationStage {
    public VolumeEvaluationStage(Resource resource, String taskName) {
        super(resource, taskName);
    }

    public VolumeEvaluationStage(Resource resource) {
        super(resource);
    }

    @Override
    protected Protos.Resource getFulfilledResource(MesosResource mesosResource) {
        Resource.Builder builder = super.getFulfilledResource(mesosResource).toBuilder();
        Optional<DiskInfo> diskInfo = getFulfilledDiskInfo(mesosResource);

        if (diskInfo.isPresent()) {
            builder.setDisk(diskInfo.get());
        }

        return builder.build();
    }

    private Optional<DiskInfo> getFulfilledDiskInfo(MesosResource mesRes) {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        if (!resourceRequirement.getResource().hasDisk()) {
            return Optional.empty();
        }

        DiskInfo.Builder builder = DiskInfo.newBuilder(resourceRequirement.getResource().getDisk());
        if (mesRes.getResource().getDisk().hasSource()) {
            builder.setSource(mesRes.getResource().getDisk().getSource());
        }

        Optional<DiskInfo.Persistence> persistence = getFulfilledPersistence();
        if (persistence.isPresent()) {
            builder.setPersistence(persistence.get());
        }

        return Optional.of(builder.build());
    }

    private Optional<DiskInfo.Persistence> getFulfilledPersistence() {
        ResourceRequirement resourceRequirement = getResourceRequirement();
        if (!resourceRequirement.createsVolume()) {
            return Optional.empty();
        } else {
            return Optional.of(DiskInfo.Persistence
                    .newBuilder(resourceRequirement.getResource().getDisk().getPersistence())
                    .setId(UUID.randomUUID().toString())
                    .build());
        }
    }
}
