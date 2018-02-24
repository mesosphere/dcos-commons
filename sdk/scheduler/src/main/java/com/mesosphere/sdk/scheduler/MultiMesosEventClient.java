package com.mesosphere.sdk.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.mesos.Protos;

import com.mesosphere.sdk.storage.PersisterUtils;

/**
 * Mesos client which wraps other clients. Used in the case where multiple services should be subscribed to mesos
 * events.
 */
public class MultiMesosEventClient implements MesosEventClient {

    private static class NamespacedResourceWrapper implements MesosEventClient.ResourceServer {

        private final SchedulerApiServer server;
        private final String namespace;

        private NamespacedResourceWrapper(SchedulerApiServer server, String namespace) {
            this.server = server;
            this.namespace = namespace;
        }

        @Override
        public void addResources(String parentNamespace, Collection<Object> resources) {
            // Example: "v1/<namespace>/<resourcepath>" (with parentNamespace="v1")
            server.addResources(PersisterUtils.join(parentNamespace, namespace), resources);
        }

    }

    private static final Object lock = new Object();

    private final Map<String, MesosEventClient> clients;
    private SchedulerApiServer apiServer;

    public MultiMesosEventClient() {
        this.clients = new HashMap<>();
    }

    /**
     * Adds a client which is mapped for the specified name.
     *
     * @param name the unique name of the client
     * @param client the client to add
     * @return {@code this}
     * @throws IllegalArgumentException if the name is already present
     */
    public MultiMesosEventClient putClient(String name, MesosEventClient client) {
        synchronized (lock) {
            MesosEventClient previousClient = clients.put(name, client);
            if (previousClient != null) {
                // Put the old client back before throwing...
                clients.put(name, previousClient);
                throw new IllegalArgumentException("Client named '" + name + "' is already present");
            }
            if (apiServer != null) {
                // Add the resources to our apiServer, using the provided name as a namespace:
                client.setResourceServer(new NamespacedResourceWrapper(apiServer, name));
            }
        }
        return this;
    }

    /**
     * Removes a client mapping which was previously added using the provided name.
     *
     * @param name the name of the client to remove
     * @return the removed client, or {@code null} if no client with that name was found
     */
    public MesosEventClient removeClient(String name) {
        synchronized (lock) {
            if (apiServer != null) {
                apiServer.removeResources(name);
            }
            return clients.remove(name);
        }
    }

    @Override
    public void register(boolean reRegistered) {
        synchronized (lock) {
            clients.values().stream().forEach(c -> c.register(reRegistered));
        }
    }

    @Override
    public OfferResponse offers(List<Protos.Offer> offers) {
        // If we don't have any sub-clients, then WE aren't ready.
        boolean allNotReady = true;

        List<Protos.Offer> unusedOffers = new ArrayList<>();
        unusedOffers.addAll(offers);

        synchronized (lock) {
            for (MesosEventClient client : clients.values()) {
                OfferResponse response = client.offers(unusedOffers);
                // Create a new list with unused offers. Avoid clearing in-place, in case response is the original list.
                unusedOffers = new ArrayList<>();
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
    public StatusResponse status(Protos.TaskStatus status) {
        // TODO(nickbp) for the multi-service case:
        // - embed the service id in task ids
        // - use status.task_id to map status => service (or kill task here if service id is unknown or invalid)
        synchronized (lock) {
            for (MesosEventClient client : clients.values()) {
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
    public void setResourceServer(ResourceServer resourceServer) {
        if (!(resourceServer instanceof SchedulerApiServer)) {
            throw new IllegalArgumentException(
                    "Expected " + SchedulerApiServer.class.getCanonicalName() +
                    ", got " + resourceServer.getClass().getCanonicalName());
        }

        synchronized (lock) {
            this.apiServer = (SchedulerApiServer) resourceServer;
            for (Map.Entry<String, MesosEventClient> entry : clients.entrySet()) {
                // Add the resources to the apiServer, using the provided name as a namespace:
                entry.getValue().setResourceServer(new NamespacedResourceWrapper(apiServer, entry.getKey()));
            }
        }
    }
}
