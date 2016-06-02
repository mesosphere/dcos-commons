package org.apache.mesos.stream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.net.Socket;

/**
 * Provides Steam utility functions.
 */
public class StreamUtil {
  private static final Logger log = LoggerFactory.getLogger(StreamUtil.class);

  /**
   * Redirects a process to STDERR and STDOUT for logging and debugging purposes.
   */
  public static void redirectProcess(Process process, PrintStream out, PrintStream err) {
    StreamRedirect stdoutRedirect = getStreamRedirectRunnable(process.getInputStream(), out);
    new Thread(stdoutRedirect).start();
    StreamRedirect stderrRedirect = getStreamRedirectRunnable(process.getErrorStream(), err);
    new Thread(stderrRedirect).start();
  }

  public static StreamRedirect getStreamRedirectRunnable(InputStream stream, PrintStream outputStream) {
    return new StreamRedirect(stream, outputStream);
  }

  public static void redirectProcess(Process process) {
    redirectProcess(process, System.out, System.err);
  }

  public static void closeQuietly(Socket socket) {
    IOUtils.closeQuietly(socket);
  }

  public static void closeQuietly(InputStream input) {
    IOUtils.closeQuietly(input);
  }

  public static void closeQuietly(OutputStream output) {
    IOUtils.closeQuietly(output);
  }

  public static void flush(OutputStream out) throws IOException {
    if (out != null) {
      out.flush();
    }
  }

  public static void flushQuietly(OutputStream out) throws IOException {
    try {
      flush(out);
    } catch (IOException e) {
      log.debug("ignoring exception: " + e.getMessage());
    }
  }

  public static void closeQuietly(Reader reader) {
    IOUtils.closeQuietly(reader);
  }
}
