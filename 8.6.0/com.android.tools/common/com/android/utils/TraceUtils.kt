/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.utils

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.System.identityHashCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Static methods useful for tracing. */
object TraceUtils {
  private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

  /** Current stack trace of the caller as a string. */
  @JvmStatic
  val currentStack: String
    get() = getCurrentStack(1)

  /** Stack traces of all threads as a single string. */
  @JvmStatic
  val stacksOfAllThreads: String
    get() {
      val buf = StringBuilder()
      for ((thread, stackTrace) in Thread.getAllStackTraces()) {
        if (buf.isNotEmpty()) {
          buf.append('\n')
        }
        buf.append(thread.toString())
        buf.append('\n')
        for (frame in stackTrace) {
          buf.append("  at ")
          buf.append(frame.toString())
          buf.append('\n')
        }
      }
      return buf.toString()
    }

  /**
   * Returns the current stack of the caller. Optionally, removes [numberOfTopFramesToRemove] frames
   * at the top of the stack.
   */
  @JvmStatic
  fun getCurrentStack(numberOfTopFramesToRemove: Int = 1): String {
    val fullStack = getStackTrace(object : Throwable() {
      override fun toString(): String {
        return ""
      }
    })
    // Remove our own frame and numberOfTopFramesToRemove frames requested by the caller.
    var start = 0
    // The first character of the stack is always '\n'.
    for (i in 0 until numberOfTopFramesToRemove.coerceAtLeast(0) + 2) {
      val pos = fullStack.indexOf('\n', start)
      if (pos < 0) {
        break
      }
      start = pos + 1
    }
    return fullStack.substring(start)
  }

  /** Returns a stack trace of the given [throwable] as a string. */
  @JvmStatic
  fun getStackTrace(throwable: Throwable): String {
    val stringWriter = StringWriter()
    PrintWriter(stringWriter).use { writer ->
      throwable.printStackTrace(writer)
      return stringWriter.toString()
    }
  }

  /**
   * A string consisting of the object's class name without the package part, '@' separator,
   * and the hexadecimal identity hash code, e.g. AndroidResGroupNode@5A1D1719.
   */
  @JvmStatic
  val Any?.simpleId: String
    get() {
      return this?.let {
        String.format("%s@%08X", javaClass.name.substringAfterLast('.'), identityHashCode(this))
      } ?: "null"
    }

  /**
   * A string containing comma-separated simple IDs of the elements of this iterable.
   * Each simple ID is the object's class name without the package part, '@' separator,
   * and the hexadecimal identity hash code, e.g. AndroidResGroupNode@5A1D1719.
   */
  @JvmStatic
  val Iterable<*>.simpleIds: String
    get() {
      val result = StringBuilder()
      for (element in this) {
        if (result.isNotEmpty()) {
          result.append(", ")
        }
        result.append(element.simpleId)
      }
      return result.toString()
    }

  /** The current time as a yyyy-MM-dd HH:mm:ss.SSS string. */
  @JvmStatic
  val currentTime: String
    get() = DATE_FORMAT.format(Date())
}
