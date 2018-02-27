package com.mesosphere.sdk.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.http.endpoints.*;
import com.mesosphere.sdk.http.types.JobInfoProvider;
import com.mesosphere.sdk.http.types.StringPropertyDeserializer;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

/**
 * Mesos client which wraps running jobs, routing Mesos offers/statuses to those jobs.
 */
public class JobsEventClient implements MesosEventClient {

    private final Object jobsLock = new Object();
    private final Map<String, ServiceScheduler> jobs;

    public JobsEventClient() {
        this.jobs = new HashMap<>();
    }

    /**
     * Adds a job which is mapped for the specified name.
     *
     * @param name the unique name of the client
     * @param job the client to add
     * @return {@code this}
     * @throws IllegalArgumentException if the name is already present
     */
    public JobsEventClient putJob(String name, ServiceScheduler job) {
        synchronized (jobsLock) {
            ServiceScheduler previousJob = jobs.put(name, job);
            if (previousJob != null) {
                // Put the old client back before throwing...
                jobs.put(name, previousJob);
                throw new IllegalArgumentException("Service named '" + name + "' is already present");
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
        synchronized (jobsLock) {
            return jobs.remove(name);
        }
    }

    @Override
    public void register(boolean reRegistered) {
        synchronized (jobsLock) {
            jobs.values().stream().forEach(c -> c.register(reRegistered));
        }
    }

    @Override
    public OfferResponse offers(List<Protos.Offer> offers) {
        // If we don't have any sub-clients, then WE aren't ready.
        boolean allNotReady = true;

        List<Protos.Offer> unusedOffers = new ArrayList<>();
        unusedOffers.addAll(offers);

        synchronized (jobsLock) {
            for (MesosEventClient client : jobs.values()) {
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
        synchronized (jobsLock) {
            for (MesosEventClient client : jobs.values()) {
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
        JobInfoProvider jobInfoProvider = new JobInfoProvider() {

            @Override
            public Optional<StateStore> getStateStore(String jobName) {
                ServiceScheduler scheduler = getJob(jobName);
                return scheduler == null ? Optional.empty() : Optional.of(scheduler.getStateStore());
            }

            @Override
            public Optional<ConfigStore<ServiceSpec>> getConfigStore(String jobName) {
                ServiceScheduler scheduler = getJob(jobName);
                return scheduler == null ? Optional.empty() : Optional.of(scheduler.getConfigStore());
            }

            @Override
            public Optional<PlanCoordinator> getPlanCoordinator(String jobName) {
                ServiceScheduler scheduler = getJob(jobName);
                return scheduler == null ? Optional.empty() : Optional.of(scheduler.getPlanCoordinator());
            }

            @Override
            public Collection<String> getJobs() {
                Collection<String> jobNames = new TreeSet<>(); // Alphabetical order
                synchronized (jobsLock) {
                    jobNames.addAll(jobs.keySet());
                }
                return jobNames;
            }

            private ServiceScheduler getJob(String jobName) {
                synchronized (jobsLock) {
                    return jobs.get(jobName);
                }
            }
        };
        return Arrays.asList(
                // TODO(nickbp): this will be ALWAYS HEALTHY. add some plans from parent Jobs scheduler?:
                new HealthResource(Collections.emptyList()),
                new JobsResource(jobInfoProvider),
                new JobsArtifactResource(jobInfoProvider),
                new JobsConfigResource(jobInfoProvider),
                new JobsPlansResource(jobInfoProvider),
                new JobsPodResource(jobInfoProvider),
                new JobsStateResource(jobInfoProvider, new StringPropertyDeserializer()));
    }
}
