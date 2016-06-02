package org.apache.mesos.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * Can be used to redirect the STDOUT and STDERR of a started process. Used for the executors.
 */
public class StreamRedirect implements Runnable {
  private final Logger log = LoggerFactory.getLogger(StreamRedirect.class);

  private InputStream stream;
  private PrintStream outputStream;

  public StreamRedirect(InputStream stream, PrintStream outputStream) {
    this.stream = stream;
    this.outputStream = outputStream;
  }

  public void run() {
    try {
      InputStreamReader streamReader = new InputStreamReader(stream, Charset.defaultCharset());
      BufferedReader streamBuffer = new BufferedReader(streamReader);

      String streamLine;
      while ((streamLine = streamBuffer.readLine()) != null) {
        outputStream.println(streamLine);
      }
    } catch (IOException ioe) {
      log.error("Stream redirect error", ioe);
    }
  }
}
