/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.androidtest.instrument

import com.android.utils.GrabProcessOutput
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Executes Android instrumentation tests on a given device via the `am instrument` command.
 *
 * This class builds and runs the `am instrument -r -w` command, capturing and parsing
 * the raw output in real-time to report test events.
 *
 * @param adb The ADB executable [File].
 * @param deviceSerial The serial number of the target Android device.
 * @param instrumentationRunnerClass The fully qualified name of the instrumentation runner
 * (e.g., `androidx.test.runner.AndroidJUnitRunner`).
 * @param instrumentationTargetPackageId The package ID of the application to be instrumented.
 * @param logger An optional [Logger] for recording command outputs and warnings.
 * @param processBuilder A factory for creating [ProcessBuilder] instances, primarily
 * exposed for testing purposes to allow mocking of process execution.
 */
class AmInstrumentationRunner(
  private val adb: File,
  private val deviceSerial: String,
  private val instrumentationRunnerClass: String,
  private val instrumentationTargetPackageId: String,
  private val logger: Logger = Logging.getLogger(AmInstrumentationRunner::class.java),
  private val processBuilder: (command: List<String>) -> ProcessBuilder = { ProcessBuilder(it) }
) {

  /**
   * Runs the `am instrument` command for the configured target.
   *
   * This method constructs the command, launches the process, and synchronously captures
   * and parses the output until the process terminates. Standard output is parsed as
   * test events, while standard error is logged as warnings.
   */
  fun runAmInstrumentCommand() {
    val command = getAmInstrumentCmd()
    val process = processBuilder(command).start()
    val parser = AmInstrumentationParser()
    val handler = object: GrabProcessOutput.IProcessOutput {
      override fun out(line: String?) { line?.let { parser.parse(it) } }
      override fun err(line: String?) { line?.let { logger.warn(line) } }
    }

    GrabProcessOutput.grabProcessOutput(
      process,
      GrabProcessOutput.Wait.WAIT_FOR_READERS,
      handler,
      null,
      TimeUnit.MILLISECONDS)

    parser.done()
  }

  private fun getAmInstrumentCmd(): List<String> {
    return listOf(
      adb.absolutePath,
      "-s", deviceSerial,
      "shell", "am", "instrument",
      "-r",  // Outputs results in raw format
      "-w",  // Forces am instrument to wait until the instrumentation terminates before terminating itself.
      "${instrumentationTargetPackageId}/${instrumentationRunnerClass}",
    )
  }
}
