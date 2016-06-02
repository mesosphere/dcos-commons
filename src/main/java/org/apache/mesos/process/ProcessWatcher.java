package org.apache.mesos.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Invokes the specified handler on process exit.
 */
public class ProcessWatcher {
  private final Logger log = LoggerFactory.getLogger(ProcessWatcher.class);
  private ProcessFailureHandler handler;
  private volatile AtomicBoolean ignore = new AtomicBoolean(false);

  public ProcessWatcher(ProcessFailureHandler handler) {
    this.handler = handler;
  }

  public void watch(final Process proc) {
    watch(proc, "");
  }

  public void watch(final Process proc, final String message) {
    log.info("Watching process: " + proc);

    Runnable r = new Runnable() {
      public void run() {
        try {
          proc.waitFor();
        } catch (Exception ex) {
          log.error("Process excited with exception: " + ex);
        }

        log.error("Failing process: " + message);
        log.error("Handling failure of process code: " + proc.exitValue());
        if (!ignore.get()) {
          handler.handle();
        }
      }
    };

    new Thread(r).start();
  }

  public void cancelWatch() {
    ignore.set(true);
  }
}
