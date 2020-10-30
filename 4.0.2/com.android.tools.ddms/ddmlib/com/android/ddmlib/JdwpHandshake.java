/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ddmlib;

import java.nio.ByteBuffer;

public class JdwpHandshake {
  // results from findHandshake
  public static final int HANDSHAKE_GOOD = 1;
  public static final int HANDSHAKE_NOTYET = 2;
  public static final int HANDSHAKE_BAD = 3;
  // this is sent and expected at the start of a JDWP connection
  private static final byte[] HANDSHAKE = {
      'J', 'D', 'W', 'P', '-', 'H', 'a', 'n', 'd', 's', 'h', 'a', 'k', 'e'
  };
  public static final int HANDSHAKE_LEN = HANDSHAKE.length;

  /**
   * Like findPacket(), but when we're expecting the JDWP handshake.
   *
   * Returns one of:
   *   HANDSHAKE_GOOD   - found handshake, looks good
   *   HANDSHAKE_BAD    - found enough data, but it's wrong
   *   HANDSHAKE_NOTYET - not enough data has been read yet
   */
  static int findHandshake(ByteBuffer buf) {
      int count = buf.position();
      int i;

      if (count < HANDSHAKE.length)
          return HANDSHAKE_NOTYET;

      for (i = HANDSHAKE.length - 1; i >= 0; --i) {
          if (buf.get(i) != HANDSHAKE[i])
              return HANDSHAKE_BAD;
      }

      return HANDSHAKE_GOOD;
  }

  /**
   * Remove the handshake string from the buffer.
   *
   * On entry and exit, "position" is the #of bytes in the buffer.
   */
  static void consumeHandshake(ByteBuffer buf) {
      // in theory, nothing else can have arrived, so this is overkill
      buf.flip();         // limit<-posn, posn<-0
      buf.position(HANDSHAKE.length);
      buf.compact();      // shift posn...limit, posn<-pending data
  }

  /**
   * Copy the handshake string into the output buffer.
   *
   * On exit, "buf"s position will be advanced.
   */
  static void putHandshake(ByteBuffer buf) {
      buf.put(HANDSHAKE);
  }
}
