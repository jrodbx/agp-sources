/*
 * Copyright (C) 2021 The Android Open Source Project
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
/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.ddmlib

import com.google.common.annotations.VisibleForTesting
import kotlin.jvm.Throws

abstract class EmulatorConsole {
  /** Disconnect the socket channel and remove self from emulator console cache.  */
  abstract fun close()

  /**
   * Sends a KILL command to the emulator.
   */
  abstract fun kill()

  /**
   * The AVD name. If the command failed returns the error message after "KO: " or null.
   */
  abstract val avdName: String?

  /**
   * The absolute path to the virtual device in the file system. The path is operating
   * system dependent; it will have / name separators on Linux and \ separators on Windows.
   *
   * @throws CommandFailedException If the subcommand failed or if the emulator's version is older
   * than 30.0.18
   */
  abstract val avdPath: String
      @Throws(CommandFailedException::class) get

  abstract fun startEmulatorScreenRecording(args: String?): String?

  abstract fun stopScreenRecording(): String?

  companion object {
    private val sTestConsoles: MutableMap<String, EmulatorConsole> = HashMap()

    /**
     * Register a console instance corresponding to the given device to be used during testing.
     * You must call [.clearConsolesForTest] at the end of your test.
     */
    @VisibleForTesting
    fun registerConsoleForTest(deviceSerial: String, console: EmulatorConsole) {
      sTestConsoles[deviceSerial] = console
    }

    /**
     * This must be called at the end of any test where
     * [.registerConsoleForTest] is called.
     */
    @VisibleForTesting
    fun clearConsolesForTest() {
      sTestConsoles.clear()
    }

    @JvmStatic
    fun getConsole(d: IDevice): EmulatorConsole? {
      return sTestConsoles[d.serialNumber] ?: EmulatorConsoleImpl.createConsole(d)
    }
  }
}
