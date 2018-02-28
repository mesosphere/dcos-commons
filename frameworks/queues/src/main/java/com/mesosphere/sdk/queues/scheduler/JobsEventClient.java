package com.mesosphere.sdk.queues.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.http.endpoints.*;
import com.mesosphere.sdk.http.types.StringPropertyDeserializer;
import com.mesosphere.sdk.queues.http.endpoints.*;
import com.mesosphere.sdk.scheduler.MesosEventClient;
import com.mesosphere.sdk.scheduler.ServiceScheduler;

/**
 * Mesos client which wraps running jobs, routing Mesos offers/statuses to those jobs.
 */
public class JobsEventClient implements MesosEventClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobsEventClient.class);

    private final DefaultJobInfoProvider jobInfoProvider;

    public JobsEventClient() {
        jobInfoProvider = new DefaultJobInfoProvider();
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
        LOGGER.info("putJob {}", name);
        Map<String, ServiceScheduler> jobs = jobInfoProvider.lockRW();
        try {
            ServiceScheduler previousJob = jobs.put(name, job);
            if (previousJob != null) {
                // Put the old client back before throwing...
                jobs.put(name, previousJob);
                throw new IllegalArgumentException("Service named '" + name + "' is already present");
            }
            return this;
        } finally {
            jobInfoProvider.unlockRW();
        }
    }

    /**
     * Removes a client mapping which was previously added using the provided name.
     *
     * @param name the name of the client to remove
     * @return the removed client, or {@code null} if no client with that name was found
     */
    public MesosEventClient removeJob(String name) {
        LOGGER.info("removeJob {}", name);
        Map<String, ServiceScheduler> jobs = jobInfoProvider.lockRW();
        try {
            return jobs.remove(name);
        } finally {
            jobInfoProvider.unlockRW();
        }
    }

    @Override
    public void register(boolean reRegistered) {
        LOGGER.info("register reRegistered={}", reRegistered);
        Collection<ServiceScheduler> jobs = jobInfoProvider.lockR();
        try {
            jobs.stream().forEach(c -> c.register(reRegistered));
        } finally {
            jobInfoProvider.unlockR();
        }
    }

    @Override
    public OfferResponse offers(List<Protos.Offer> offers) {
        LOGGER.info("offers={}", offers.size());
        // If we don't have any sub-clients, then WE aren't ready.
        boolean allNotReady = true;

        List<Protos.Offer> unusedOffers = new ArrayList<>();
        unusedOffers.addAll(offers);

        Collection<ServiceScheduler> jobs = jobInfoProvider.lockR();
        try {
            for (MesosEventClient job : jobs) {
                OfferResponse response = job.offers(unusedOffers);
                // Create a new list with unused offers. Avoid clearing in-place, in case response is the original list.
                unusedOffers = new ArrayList<>();
                unusedOffers.addAll(response.unusedOffers);
                LOGGER.info("  response={}, unusedOffers={}", response.result, unusedOffers.size());

                if (response.result != OfferResponse.Result.NOT_READY) {
                    allNotReady = false;
                }

                // If we run out of unusedOffers we still keep going with an empty list of offers.
                // This is done in case any of the clients depends on us to turn the crank periodically.
            }
        } finally {
            jobInfoProvider.unlockR();
        }

        LOGGER.info("  allNotReady={}", allNotReady);
        return allNotReady
                ? OfferResponse.notReady(unusedOffers)
                : OfferResponse.processed(unusedOffers);
    }

    @Override
    public StatusResponse status(Protos.TaskStatus status) {
        LOGGER.info("status {}={}", status.getTaskId(), status.getState());
        // TODO(nickbp) for the multi-service case:
        // - embed the service id in task ids
        // - use status.task_id to map status => service (or kill task here if service id is unknown or invalid)
        Collection<ServiceScheduler> jobs = jobInfoProvider.lockR();
        try {
            for (MesosEventClient job : jobs) {
                StatusResponse response = job.status(status);
                LOGGER.info("  response={}", response.result);
                if (response.result == StatusResponse.Result.PROCESSED) {
                    // Stop as soon as we find a matching service.
                    return response;
                }
            }
        } finally {
            jobInfoProvider.unlockR();
        }
        // Nobody recognized this task.
        LOGGER.info("  unknown task");
        return StatusResponse.unknownTask();
    }

    @Override
    public Collection<Object> getResources() {
        return Arrays.asList(
                // TODO(nickbp): this will be ALWAYS HEALTHY... Options:
                // - add some plans from a parent queue scheduler? (if we should have one)
                // - implement a custom HealthResource which isn't plan-based?
                new HealthResource(Collections.emptyList()),
                new JobsResource(jobInfoProvider),
                new JobsArtifactResource(jobInfoProvider),
                new JobsConfigResource(jobInfoProvider),
                new JobsPlansResource(jobInfoProvider),
                new JobsPodResource(jobInfoProvider),
                new JobsStateResource(jobInfoProvider, new StringPropertyDeserializer()));
    }
}
