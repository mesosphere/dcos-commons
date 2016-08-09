package org.apache.mesos.scheduler;

import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.apache.mesos.v1.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericHTTPSchedulerImpl implements Scheduler {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(GenericHTTPSchedulerImpl.class);

    private org.apache.mesos.Scheduler wrappedScheduler;

    public GenericHTTPSchedulerImpl(org.apache.mesos.Scheduler wrappedScheduler) {
        this.wrappedScheduler = wrappedScheduler;
    }

    @Override
    public void connected(Mesos mesos) {
        LOGGER.info("Connected!");
    }

    @Override
    public void disconnected(Mesos mesos) {
        LOGGER.info("Disconnected!");
    }

    @Override
    public void received(Mesos mesos, Protos.Event event) {
        final SchedulerDriverAdaptorMesos schedulerDriver = new SchedulerDriverAdaptorMesos(mesos);
        LOGGER.info("Received event: {}", event);
        switch (event.getType()) {
            case SUBSCRIBED: {
                org.apache.mesos.v1.Protos.FrameworkID frameworkId = event.getSubscribed().getFrameworkId();
                // Trigger reconcile
                // Change state to SUBSCRIBED

                LOGGER.info("Subscribed with ID " + frameworkId);
                break;
            }

            case OFFERS: {
                LOGGER.info("Received an OFFERS event");
                break;
            }

            case RESCIND: {
                LOGGER.info("Received an RESCIND event");
                break;
            }

            case UPDATE: {
                LOGGER.info("Received an UPDATE event");
                break;
            }

            case MESSAGE: {
                LOGGER.info("Received a MESSAGE event");
                break;
            }

            case FAILURE: {
                LOGGER.info("Received a FAILURE event");
                break;
            }

            case ERROR: {
                LOGGER.info("Received an ERROR event");
                System.exit(1);
            }

            case HEARTBEAT: {
                LOGGER.info("Received a HEARTBEAT event");
                break;
            }

            case UNKNOWN: {
                LOGGER.info("Received an UNKNOWN event");
                break;
            }
        }
    }
}
