package org.apache.mesos.collections;

import java.util.Arrays;

/**
 * Array utilities.
 */
public class ArrayUtil {

  /**
   * useful when working with buffers.  the buffer size may be 1024 with 512 bytes copied in.  To remove
   * the remaining extra 0s from the array use this trim.  Beware, do not use String.trim() unless you
   * want the \n or \r removed.
   *
   * @param bytes
   * @return
   */
  public static byte[] trim(byte[] bytes) {
    int i = bytes.length - 1;
    while (i >= 0 && bytes[i] == 0) {
      --i;
    }

    return Arrays.copyOf(bytes, i + 1);
  }
}
