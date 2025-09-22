/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.sdklib.internal.avd

/**
 * Keys to config entries in an AVD's user-settings.ini file (which resides within the AVD's data
 * folder).
 */
object UserSettingsKey {
  /**
   * The ABI that applications should be built with for this device. (This allows testing an app
   * using alternative ABIs via binary translation, rather than the native ABI of the device.)
   */
  const val PREFERRED_ABI = "abi.type.preferred"

  /** The path to the emulator binary, relative to the emulator SDK package. */
  const val EMULATOR_BINARY = "emulatorBinary"

  /** Extra command-line options to pass to the emulator. */
  const val COMMAND_LINE_OPTIONS = "commandLineOptions"
}
