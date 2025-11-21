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

/** An AVD setting that determines how the emulator should boot. */
sealed class BootMode {
  /** A user-visible text representation of this boot mode. */
  abstract val text: String

  abstract fun properties(): Map<String, String>

  companion object {
    fun fromProperties(properties: Map<String, String>): BootMode {
      val snapshot = properties[ConfigKey.CHOSEN_SNAPSHOT_FILE]
      return when {
        properties[ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE] == "yes" &&
          !snapshot.isNullOrBlank() -> BootSnapshot(snapshot)
        properties[ConfigKey.FORCE_COLD_BOOT_MODE] == "yes" -> ColdBoot
        else -> QuickBoot
      }
    }
  }
}

// TODO: make this a data object when Kotlin 1.9 is available
object QuickBoot : BootMode() {
  override val text = "Quick Boot"

  override fun properties(): Map<String, String> =
    mapOf(
      ConfigKey.CHOSEN_SNAPSHOT_FILE to "",
      ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE to "no",
      ConfigKey.FORCE_COLD_BOOT_MODE to "no",
      ConfigKey.FORCE_FAST_BOOT_MODE to "yes",
    )

  override fun toString() = this::class.simpleName!!
}

object ColdBoot : BootMode() {
  override val text = "Cold Boot"

  override fun properties(): Map<String, String> =
    mapOf(
      ConfigKey.CHOSEN_SNAPSHOT_FILE to "",
      ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE to "no",
      ConfigKey.FORCE_COLD_BOOT_MODE to "yes",
      ConfigKey.FORCE_FAST_BOOT_MODE to "no",
    )

  override fun toString() = this::class.simpleName!!
}

data class BootSnapshot(val snapshot: String) : BootMode() {
  override val text = snapshot

  override fun properties(): Map<String, String> =
    mapOf(
      ConfigKey.CHOSEN_SNAPSHOT_FILE to snapshot,
      ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE to "yes",
      ConfigKey.FORCE_COLD_BOOT_MODE to "no",
      ConfigKey.FORCE_FAST_BOOT_MODE to "no",
    )
}
