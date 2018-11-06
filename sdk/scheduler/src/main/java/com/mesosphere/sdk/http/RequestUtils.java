package com.mesosphere.sdk.http;

import com.mesosphere.sdk.http.types.TaskInfoAndStatus;

import com.google.api.client.util.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utilities for handling HTTP requests from clients.
 */
public final class RequestUtils {

  private static final String NO_PAYLOAD_ERROR_MESSAGE = "Missing payload";

  private static final String TOO_BIG_ERROR_MESSAGE = "Stream exceeds %d byte size limit";

  private RequestUtils() {
    // do not instantiate
  }

  /**
   * Parses a JSON list payload in the form of a string, returning the corresponding Java list.
   *
   * @throws JSONException if the provided string could not be parsed as a JSON list
   */
  public static List<String> parseJsonList(String payload) throws JSONException {
    if (StringUtils.isBlank(payload)) {
      return Collections.emptyList();
    }
    List<String> strings = new ArrayList<>();
    Iterator<Object> iter = new JSONArray(payload).iterator();
    while (iter.hasNext()) {
      strings.add(iter.next().toString());
    }
    return strings;
  }

  /**
   * Returns a new list which contains only the tasks for a given pod which matched the provided task names.
   * For example, a task named "foo" in "pod-0" will be returned if the filter contains either "foo" or "pod-0-foo".
   */
  public static Collection<TaskInfoAndStatus> filterPodTasks(
      String podName, Collection<TaskInfoAndStatus> podTasks, Set<String> taskNameFilter)
  {
    if (taskNameFilter.isEmpty()) {
      return podTasks;
    }
    // given a task named "foo" in "pod-0", allow either "foo" or "pod-0-foo" to match:
    final Set<String> prefixedTaskNameFilter = taskNameFilter.stream()
        .map(filterEntry -> String.format("%s-%s", podName, filterEntry))
        .collect(Collectors.toSet());
    List<TaskInfoAndStatus> filteredPodTasks = podTasks.stream()
        .filter(task ->
            prefixedTaskNameFilter.contains(task.getInfo().getName()) ||
                taskNameFilter.contains(task.getInfo().getName()))
        .collect(Collectors.toList());
    return filteredPodTasks;
  }

  /**
   * Reads the content of the provided payload, enforcing the specified size limit, while being permissive/dismissive
   * about provided Content-Length in the header metadata.
   *
   * @param inputStream The input stream containing the data
   * @param fileDetails The HTTP header details of the stream
   * @param sizeLimit   limit in bytes for the returned data, or no limit if <= 0
   * @return the resulting buffer
   * @throws IllegalArgumentException if the stream is missing, or its size exceeds {@code sizeLimit}
   * @throws IOException              if copying the stream's data failed
   */
  public static byte[] readData(
      InputStream inputStream,
      FormDataContentDisposition fileDetails,
      int sizeLimit)
      throws IOException
  {
    if (inputStream == null) {
      throw new IllegalArgumentException(NO_PAYLOAD_ERROR_MESSAGE);
    }
    // If size limit is enabled, check the declared size in the metadata, if any:
    if (sizeLimit > 0) {
      if (fileDetails != null && fileDetails.getSize() > (long) sizeLimit) {
        throw new IllegalArgumentException(String.format(TOO_BIG_ERROR_MESSAGE, sizeLimit));
      }
      // Set capacity to one byte greater than sizeLimit: "Canary byte" to detect when sizeLimit is exceeded.
      inputStream = new BoundedInputStream(inputStream, sizeLimit + 1); // SUPPRESS CHECKSTYLE ParameterAssignment
    }

    // If Content-Length is provided (and is less than the limit), use it to initialize the buffer.
    int bufLen = (fileDetails != null && fileDetails.getSize() > 0)
        ? (int) fileDetails.getSize()
        : 64;
    ByteArrayOutputStream outStream = new ByteArrayOutputStream(bufLen);

    // We could use IOUtils.copyLarge() and set the length to fileDetails.getSize(), but let's be permissive of
    // missing Content-Length (as long as the actual streamed length doesn't exceed sizeLimit).
    IOUtils.copy(inputStream, outStream);

    // Size limit: Check whether our "canary byte" was actually used to read the stream. If it was, then complain.
    if (sizeLimit > 0 && outStream.size() > sizeLimit) {
      throw new IllegalArgumentException(String.format(TOO_BIG_ERROR_MESSAGE, sizeLimit));
    }
    // Returned array's length matches the actual streamed length, regardless of the Content-Length value.
    return outStream.toByteArray();
  }
}
