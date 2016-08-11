package org.apache.mesos.scheduler;

import org.apache.mesos.protobuf.Devolver;
import org.apache.mesos.protobuf.Evolver;
import org.apache.mesos.v1.scheduler.Mesos;
import org.apache.mesos.v1.scheduler.Protos;
import org.apache.mesos.v1.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * V1 HTTP Scheduler Impl.
 */
public class V1SchedulerImpl implements Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(V1SchedulerImpl.class);

    // Exponential back-off between consecutive subscribe calls.
    private static final int MULTIPLIER = 2;
    private static final long SEED_BACKOFF_MS = 2000;
    private static final long MAX_BACKOFF_MS = 30000;

    private org.apache.mesos.Scheduler wrappedScheduler;
    private AtomicBoolean registered = new AtomicBoolean();
    private org.apache.mesos.Protos.FrameworkID frameworkId;
    private org.apache.mesos.Protos.FrameworkInfo frameworkInfo;

    private Timer subscriberTimer;
    private long backOffMs;

    public V1SchedulerImpl(org.apache.mesos.Scheduler wrappedScheduler,
                           org.apache.mesos.Protos.FrameworkInfo frameworkInfo) {
        this.wrappedScheduler = wrappedScheduler;
        this.frameworkInfo = frameworkInfo;
    }

    @Override
    public void connected(Mesos mesos) {
        LOGGER.info("Connected!");
        performReliableSubscription(mesos);
    }

    public class SubscriberTask extends TimerTask {
        private Mesos mesos;

        public SubscriberTask(Mesos mesos) {
            this.mesos = mesos;
        }

        @Override
        public void run() {
            LOGGER.info("Sending SUBSCRIBE call");
            Protos.Call.Builder callBuilder = Protos.Call.newBuilder()
                    .setType(Protos.Call.Type.SUBSCRIBE)
                    .setSubscribe(Protos.Call.Subscribe.newBuilder()
                            .setFrameworkInfo(Evolver.evolve(frameworkInfo))
                            .build());
            if (frameworkInfo.hasId()) {
                callBuilder.setFrameworkId(Evolver.evolve(frameworkInfo.getId()));
            }
            mesos.send(callBuilder.build());
            long nextBackoff = MULTIPLIER * backOffMs;
            backOffMs = Math.min(nextBackoff > 0 ? nextBackoff : 0, MAX_BACKOFF_MS);
            LOGGER.info("Backing off for: {}ms", backOffMs);
            subscriberTimer.schedule(new SubscriberTask(mesos), backOffMs);
        }
    }

    private void performReliableSubscription(Mesos mesos) {
        // If timer is not running, initialize it.
        if (subscriberTimer == null) {
            initSubscriber();
            subscriberTimer.schedule(new SubscriberTask(mesos), backOffMs);
        }
    }

    private void initSubscriber() {
        LOGGER.info("Initializing reliable subscriber...");
        backOffMs = SEED_BACKOFF_MS;
        subscriberTimer = new Timer();
    }

    private void cancelSubscriber() {
        LOGGER.info("Cancelling subscriber...");
        if (subscriberTimer != null) {
            subscriberTimer.cancel();
            subscriberTimer.purge();
        }
        subscriberTimer = null;
    }

    @Override
    public void disconnected(Mesos mesos) {
        LOGGER.info("Disconnected!");
        cancelSubscriber();
    }

    @Override
    public void received(Mesos mesos, Protos.Event _event) {
        // TODO(anand): Fix the FrameworkInfo argument.
        final MesosToSchedulerDriverAdapter schedulerDriver = new MesosToSchedulerDriverAdapter(mesos, frameworkId);
        org.apache.mesos.scheduler.Protos.Event event = Devolver.devolve(_event);
        LOGGER.info("Received event: {}", event);

        switch (event.getType()) {
            case SUBSCRIBED: {
                this.frameworkId = event.getSubscribed().getFrameworkId();
                schedulerDriver.setFrameworkId(frameworkId);
                if (!registered.get()) {
                    registered.set(true);
                    wrappedScheduler.registered(schedulerDriver, frameworkId, null /* MasterInfo */);
                } else {
                    // Invoke re-registered
                    wrappedScheduler.reregistered(schedulerDriver, null /* MasterInfo */);
                }

                cancelSubscriber();

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
                final org.apache.mesos.v1.Protos.TaskStatus v1Status = _event.getUpdate().getStatus();
                final org.apache.mesos.v1.Protos.AgentID agentId = v1Status.getAgentId();
                final org.apache.mesos.v1.Protos.TaskID taskId = v1Status.getTaskId();
                final org.apache.mesos.Protos.TaskStatus status = event.getUpdate().getStatus();
                wrappedScheduler.statusUpdate(schedulerDriver, status);
                mesos.send(Protos.Call.newBuilder()
                        .setType(Protos.Call.Type.ACKNOWLEDGE)
                        .setFrameworkId(Evolver.evolve(frameworkId))
                        .setAcknowledge(Protos.Call.Acknowledge.newBuilder()
                                .setAgentId(agentId)
                                .setTaskId(taskId)
                                .setUuid(status.getUuid())
                                .build())
                        .build());

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

    public org.apache.mesos.Scheduler getWrappedScheduler() {
        return wrappedScheduler;
    }
}
