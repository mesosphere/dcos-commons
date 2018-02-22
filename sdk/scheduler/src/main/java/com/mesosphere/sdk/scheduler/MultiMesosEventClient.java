package com.mesosphere.sdk.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskStatus;

/**
 * Mesos client which wraps other clients. Used in the case where multiple services should be subscribed to mesos
 * events.
 */
public class MultiMesosEventClient implements MesosEventClient {

    private static final Object lock = new Object();
    private final List<MesosEventClient> clients;

    public MultiMesosEventClient() {
        this.clients = new ArrayList<>();
    }

    public MultiMesosEventClient addClient(MesosEventClient client) {
        //TODO(nickbp): Should revive offers after a new client is added.. and consider invoking its register() call too
        clients.add(client);
        return this;
    }

    @Override
    public void register(boolean reRegistered) {
        synchronized (lock) {
            clients.stream().forEach(c -> c.register(reRegistered));
        }
    }

    @Override
    public OfferResponse offers(List<Offer> offers) {
        boolean allNotReady = true;
        List<Offer> unusedOffers = new ArrayList<>();
        unusedOffers.addAll(offers);

        synchronized (lock) {
            for (MesosEventClient client : clients) {
                OfferResponse response = client.offers(unusedOffers);
                // Update the list of unused offers:
                unusedOffers.clear();
                unusedOffers.addAll(response.unusedOffers);

                if (response.result != OfferResponse.Result.NOT_READY) {
                    allNotReady = false;
                }

                // If we run out of unusedOffers we still keep going with an empty list of offers.
                // This is done in case any of the clients depends on us to turn the crank periodically.
            }
        }

        return allNotReady
                ? OfferResponse.notReady(unusedOffers)
                : OfferResponse.processed(unusedOffers);
    }

    @Override
    public StatusResponse status(TaskStatus status) {
        // TODO(nickbp) for the multi-service case:
        // - embed the service id in task ids
        // - use status.task_id to map status => service (or kill task here if service id is unknown or invalid)
        synchronized (lock) {
            for (MesosEventClient client : clients) {
                StatusResponse response = client.status(status);
                if (response.result == StatusResponse.Result.PROCESSED) {
                    // Stop as soon as we find a matching service.
                    return response;
                }
            }
        }
        // Nobody recognized this task.
        return StatusResponse.unknownTask();
    }

    @Override
    public Collection<Object> getResources() {
        // TODO(nickbp): Produce namespaced resources somehow. SchedulerApiServer has hints...
        return Collections.emptyList();
    }
}
