package com.mesosphere.sdk.http.endpoints;

import com.codahale.metrics.jvm.ThreadDump;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import java.lang.management.ManagementFactory;

/**
 * A read-only API for accessing the current threads and thread state of the scheduler.
 */
@Path("/v1/debug")
public class ThreadsResource {

  private final ThreadDump threadDump = new ThreadDump(ManagementFactory.getThreadMXBean());

  public ThreadsResource() {
  }

  /**
   * Renders the current threads and thread state of the scheduler.
   */
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("threads")
  public StreamingOutput getThreads() {
    return output -> {
      threadDump.dump(output);
    };
  }
}
