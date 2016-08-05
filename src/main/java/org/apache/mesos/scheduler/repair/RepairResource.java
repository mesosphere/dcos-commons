package org.apache.mesos.scheduler.repair;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple endpoint to expose repair state information.
 * <p>
 * Renders the stopped tasks and failed tasks as JSON, so that the operator can understand the status of the system.
 */
@Path("/v1/repair")
public class RepairResource {
    private static final Log log = LogFactory.getLog(RepairResource.class);

    private final AtomicReference<RepairStatus> repairStatus;

    public RepairResource(AtomicReference<RepairStatus> repairStatus) {
        this.repairStatus = repairStatus;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RepairStatus repair() {
        try {
            return repairStatus.get();
        } catch (Exception ex) {
            log.error("Failed to fetch data with exception:", ex);
            throw ex;
        }
    }
}
