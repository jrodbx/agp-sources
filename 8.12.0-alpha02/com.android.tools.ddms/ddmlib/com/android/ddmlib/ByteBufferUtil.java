/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.annotations.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ByteBufferUtil {

  @NonNull
  public static ByteBuffer mapFile(@NonNull File f, long offset, @NonNull ByteOrder byteOrder) throws IOException {
    FileInputStream dataFile = new FileInputStream(f);
    try {
      FileChannel fc = dataFile.getChannel();
      MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, offset, f.length() - offset);
      buffer.order(byteOrder);
      return buffer;
    } finally {
      dataFile.close(); // this *also* closes the associated channel, fc
    }
  }

  @NonNull
  public static String getString(@NonNull ByteBuffer buf, int len) {
      char[] data = new char[len];
      for (int i = 0; i < len; i++)
          data[i] = buf.getChar();
      return new String(data);
  }

  public static void putString(@NonNull ByteBuffer buf, @NonNull String str) {
      int len = str.length();
      for (int i = 0; i < len; i++)
          buf.putChar(str.charAt(i));
  }

  /**
   * Please use with care. In most cases leaving the job to the GC is enough.
   */
  public static boolean cleanBuffer(@NonNull ByteBuffer buffer) {
    if (!buffer.isDirect()) return true;

    // in Java 9+, the "official" dispose method is sun.misc.Unsafe#invokeCleaner
    try {
      Class<?> unsafeClass =
        ByteBufferUtil.class.getClassLoader().loadClass("sun.misc.Unsafe");
      Field f = unsafeClass.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      Object unsafe = f.get(null);
      MethodType type = MethodType.methodType(void.class, ByteBuffer.class);
      @SuppressWarnings("JavaLangInvokeHandleSignature")
      MethodHandle handle =
        MethodHandles.lookup().findVirtual(unsafeClass, "invokeCleaner", type);
      handle.invoke(unsafeClass.cast(unsafe), buffer);
      return true;
    }
    catch (Throwable ex) {
      // ignore, this is a best effort attempt.
      Log.w("ddmlib", "ByteBufferUtil.cleanBuffer() failed " + ex);
      return false;
    }
  }
}
