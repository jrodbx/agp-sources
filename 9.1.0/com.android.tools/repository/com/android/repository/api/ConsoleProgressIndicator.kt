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
package com.android.repository.api

import java.io.PrintStream
import java.util.Locale

/**
 * A simple [ProgressIndicator] that prints log messages to `stdout` and `stderr`.
 *
 * Progress rendering is done by emitting spaces followed by a carriage return, that way text can be re-rendered on the same line. For more
 * information, see https://stackoverflow.com/a/852802.
 *
 * @param canPrintProgress should be false for CI environments where 'TERM' is declared as 'dumb' to avoid flooding logs (b/137389944,
 *   b/66347650)
 */
open class ConsoleProgressIndicator
@JvmOverloads
constructor(
  private val out: PrintStream = System.out,
  private val err: PrintStream = System.err,
  private val canPrintProgress: Boolean = System.getenv("TERM") != "dumb",
) : ProgressIndicatorAdapter() {

  private var text: String? = ""
  private var secondaryText: String? = ""
  private var progress = 0.0

  private var last: String? = null

  override fun getFraction() = progress

  override fun setFraction(progress: Double) {
    this.progress = progress
    printProgress(true)
  }

  private fun printProgress(forceShowProgress: Boolean) {
    if (!canPrintProgress) {
      return
    }
    val line = StringBuilder()
    if (forceShowProgress || fraction > 0) {
      line.append("[")
      var i = 1
      while (i < PROGRESS_WIDTH * progress) {
        line.append("=")
        i++
      }
      while (i < PROGRESS_WIDTH) {
        line.append(" ")
        i++
      }
      line.append("] ")

      line.append(String.format(Locale.US, "%.0f%%", 100 * progress))
      line.append(" ")
    }
    line.append(text)
    line.append(" ")
    line.append(secondaryText)
    if (line.length > MAX_WIDTH) {
      line.delete(MAX_WIDTH, line.length)
    } else {
      line.append(SPACES, 0, MAX_WIDTH - line.length)
    }

    line.append("\r")

    // If the progress is at maximum, then append a newline so that future calls to logMessage
    // won't overlap with this output.
    if (fraction >= 1) {
      line.append(System.lineSeparator())
    }

    val result = line.toString()
    if (result != last) {
      out.print(result)
      out.flush()
      last = result
    }
  }

  private fun logMessage(s: String, e: Throwable?, stream: PrintStream) {
    // Overwrite the entire progress bar with blanks so that we can re-render it on a visibly
    // lower line at the end of this function when we call printProgress.
    //
    // There is no need to blank this out when the progress is full since we would have already
    // printed a newline character.
    if (progress > 0 && progress < 1) {
      out.print(SPACES)
      out.print("\r")
      last = null
    }
    stream.println(s)
    e?.printStackTrace()

    // Re-render the progress bar after having blanked it out.
    if (progress > 0 && progress < 1) {
      printProgress(false)
    }
  }

  override fun logWarning(s: String, e: Throwable?) {
    logMessage("Warning: $s", e, err)
  }

  override fun logError(s: String, e: Throwable?) {
    logMessage("Error: $s", e, err)
  }

  override fun logInfo(s: String) {
    logMessage("Info: $s", null, out)
  }

  override fun setText(text: String?) {
    this.text = text
    printProgress(false)
  }

  override fun setSecondaryText(text: String?) {
    secondaryText = text
    printProgress(false)
  }
}

private const val PROGRESS_WIDTH = 40
private const val MAX_WIDTH = 80
private const val SPACES = "                                                                                "
