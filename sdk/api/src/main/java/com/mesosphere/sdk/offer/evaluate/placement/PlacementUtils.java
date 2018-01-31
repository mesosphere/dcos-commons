package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities relating to operation of Placement Rules.
 */
public class PlacementUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlacementUtils.class);

    private PlacementUtils() {
        // do not instantiate
    }

    /**
     * Returns whether the provided {@link Protos.TaskInfo}, representing a previously-launched task,
     * is in the same provided pod provided in the {@link PodInstance}.
     */
    public static boolean areEquivalent(Protos.TaskInfo taskInfo, PodInstance podInstance) {
        try {
            TaskLabelReader labels = new TaskLabelReader(taskInfo);
            return labels.getType().equals(podInstance.getPod().getType())
                    && labels.getIndex() == podInstance.getIndex();
        } catch (TaskException e) {
            LOGGER.warn("Unable to extract pod type or index from TaskInfo", e);
            return false;
        }
    }
}
