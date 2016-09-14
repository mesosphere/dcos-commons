package org.apache.mesos.scheduler.recovery.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.scheduler.recovery.RecoveryStatus;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple endpoint to expose recovery state information.
 * <p>
 * Renders the stopped tasks and failed tasks as JSON, so that the operator can understand the status of the system.
 */
@Path("/v1/recovery")
public class RecoveryResource {
    private static final Log log = LogFactory.getLog(RecoveryResource.class);

    private final AtomicReference<RecoveryStatus> repairStatus;

    public RecoveryResource(AtomicReference<RecoveryStatus> repairStatus) {
        this.repairStatus = repairStatus;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RecoveryStatus repair() {
        try {
            return repairStatus.get();
        } catch (Exception ex) {
            log.error("Failed to fetch data with exception:", ex);
            throw ex;
        }
    }
}
