package org.apache.mesos.scheduler;

import org.apache.mesos.protobuf.Devolver;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.apache.mesos.v1.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * V1 HTTP Scheduler Impl.
 */
public class V1SchedulerImpl implements Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(V1SchedulerImpl.class);

    private org.apache.mesos.Scheduler wrappedScheduler;

    public V1SchedulerImpl(org.apache.mesos.Scheduler wrappedScheduler) {
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
    public void received(Mesos mesos, Protos.Event _event) {
        // TODO(anand): Fix the FrameworkInfo argument.
        final MesosToSchedulerDriverAdapter schedulerDriver = new MesosToSchedulerDriverAdapter(mesos, null);

        org.apache.mesos.scheduler.Protos.Event event = Devolver.devolve(_event);

        LOGGER.info("Received event: {}", event);

        switch (event.getType()) {
            case SUBSCRIBED: {
                org.apache.mesos.Protos.FrameworkID frameworkId = event.getSubscribed().getFrameworkId();
                // Trigger reconcile
                // Change state to SUBSCRIBED

                LOGGER.info("Subscribed with ID " + frameworkId);
                break;
            }

            case OFFERS: {
                wrappedScheduler.resourceOffers(schedulerDriver, event.getOffers().getOffersList());
                break;
            }

            case RESCIND: {
                wrappedScheduler.offerRescinded(schedulerDriver, event.getRescind().getOfferId());
                break;
            }

            case UPDATE: {
                wrappedScheduler.statusUpdate(schedulerDriver, event.getUpdate().getStatus());
                break;
            }

            case MESSAGE: {
                wrappedScheduler.frameworkMessage(
                        schedulerDriver,
                        event.getMessage().getExecutorId(),
                        event.getMessage().getSlaveId(),
                        event.getMessage().getData().toByteArray());
                break;
            }

            case FAILURE: {
                final org.apache.mesos.scheduler.Protos.Event.Failure failure = event.getFailure();
                if (failure.hasSlaveId() && failure.hasExecutorId()) {
                    wrappedScheduler.executorLost(
                            schedulerDriver,
                            failure.getExecutorId(),
                            failure.getSlaveId(),
                            failure.getStatus());
                } else {
                    wrappedScheduler.slaveLost(schedulerDriver, failure.getSlaveId());
                }
                break;
            }

            case ERROR: {
                final org.apache.mesos.scheduler.Protos.Event.Error error = event.getError();
                wrappedScheduler.error(schedulerDriver, error.getMessage());
                break;
            }

            case HEARTBEAT: {
                // TODO(Mohit)
                break;
            }

            case UNKNOWN: {
                LOGGER.error("Received an unsupported event: {}", event);
                break;
            }

            default: {
                LOGGER.error("Received an unsupported event: {}", event);
                break;
            }
        }
    }
}
