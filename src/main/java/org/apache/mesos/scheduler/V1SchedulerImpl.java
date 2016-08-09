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
                wrappedScheduler.resourceOffers(schedulerDriver, Devolver.devolve(event.getOffers()));
                break;
            }

            case RESCIND: {
                wrappedScheduler.offerRescinded(schedulerDriver, Devolver.devolve(event.getRescind().getOfferId()));
                break;
            }

            case UPDATE: {
                wrappedScheduler.statusUpdate(schedulerDriver, Devolver.devolve(event.getUpdate().getStatus()));
                break;
            }

            case MESSAGE: {
                final Protos.Event.Message message = event.getMessage();
                wrappedScheduler.frameworkMessage(
                        schedulerDriver,
                        Devolver.devolve(message.getExecutorId()),
                        Devolver.devolve(message.getAgentId()),
                        message.getData().toByteArray());
                break;
            }

            case FAILURE: {
                final Protos.Event.Failure failure = event.getFailure();
                if (failure.hasAgentId() && failure.hasExecutorId()) {
                    wrappedScheduler.executorLost(
                            schedulerDriver,
                            Devolver.devolve(failure.getExecutorId()),
                            Devolver.devolve(failure.getAgentId()),
                            failure.getStatus());
                } else {
                    wrappedScheduler.slaveLost(
                            schedulerDriver,
                            Devolver.devolve(failure.getAgentId()));
                }
                break;
            }

            case ERROR: {
                final Protos.Event.Error error = event.getError();
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
