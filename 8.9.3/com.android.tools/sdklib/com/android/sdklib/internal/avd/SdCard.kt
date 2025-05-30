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
@file:JvmName("SdCards")

package com.android.sdklib.internal.avd

import com.android.sdklib.devices.Storage
import com.android.sdklib.devices.Storage.Unit
import com.android.utils.GrabProcessOutput
import com.android.utils.GrabProcessOutput.IProcessOutput
import com.android.utils.ILogger
import java.io.IOException

sealed interface SdCard {
  fun configEntries(): Map<String, String>
}

/** An SD card image stored as "sdcard.img" in the AVD data folder that is managed by Studio. */
data class InternalSdCard(val size: Long) : SdCard {
  fun sizeSpec(): String {
    val size = Storage(size, Unit.B)
    // mksdcard will truncate the size to a multiple of 1 KiB
    val unit = size.getAppropriateUnits().coerceIn(Unit.KiB..Unit.GiB)
    return "${size.getSizeAsUnit(unit)}${unit.unitChar}"
  }

  override fun configEntries(): Map<String, String> =
    // This property is only used for display purposes when invoking 'avdmanager list avd'
    mapOf(ConfigKey.SDCARD_SIZE to sizeSpec())
}

/** An SD card image stored at an arbitrary path, managed by the user. */
data class ExternalSdCard(val path: String) : SdCard {
  override fun configEntries(): Map<String, String> = mapOf(ConfigKey.SDCARD_PATH to path)
}

fun sdCardFromConfig(config: Map<String, String>): SdCard? {
  val path = config[ConfigKey.SDCARD_PATH]
  if (path != null) {
    return ExternalSdCard(path)
  }

  val size = Storage.getStorageFromString(config[ConfigKey.SDCARD_SIZE])
  if (size != null) {
    return InternalSdCard(size.size)
  }

  return null
}

/** Pattern for matching SD card sizes, e.g. "4K" or "16M". */
private val SDCARD_SIZE_PATTERN = "(\\d+)([KMG])".toRegex()

/** Minimum size of an SD card image file in bytes, currently 9 MiB. */
const val SDCARD_MIN_BYTE_SIZE = 9L shl 20
/** Maximum size of an SD card image file in bytes, currently 1023 GiB. */
const val SDCARD_MAX_BYTE_SIZE = 1023L shl 30

/**
 * Parses an [SdCard] from the provided String. If the string matches [SDCARD_SIZE_PATTERN], returns
 * an [InternalSdCard] of the parsed size. Otherwise, the string is assumed to be a path to an
 * external SD card.
 *
 * @throws IllegalArgumentException if the size is out of range
 */
fun parseSdCard(sdcard: String): SdCard {
  val match = SDCARD_SIZE_PATTERN.matchEntire(sdcard) ?: return ExternalSdCard(sdcard)
  try {
    val sdcardSize =
      match.groupValues[1].toLong().let {
        when (match.groupValues[2]) {
          "K" -> it shl 10
          "M" -> it shl 20
          "G" -> it shl 30
          else -> throw IllegalArgumentException()
        }
      }

    if (sdcardSize in SDCARD_MIN_BYTE_SIZE..SDCARD_MAX_BYTE_SIZE) {
      return InternalSdCard(sdcardSize)
    }
  } catch (e: NumberFormatException) {
    // Fall through: this can only happen if the number is too large to fit in a long.
  }
  throw IllegalArgumentException(
    "SD card size must be in the range ${SDCARD_MIN_BYTE_SIZE shr 20}M..${SDCARD_MAX_BYTE_SIZE shr 20}M"
  )
}

/**
 * Invokes the mksdcard tool to create a new SD card image file.
 *
 * @param toolLocation The path to the mksdcard tool.
 * @param size the size of the new SD card
 * @param location The path of the new sdcard image file to generate.
 * @return True if the sdcard could be created.
 */
fun createSdCard(
  logger: ILogger,
  toolLocation: String,
  sizeSpec: String,
  location: String,
): Boolean {
  val process =
    try {
      Runtime.getRuntime().exec(arrayOf(toolLocation, sizeSpec, location))
    } catch (e: IOException) {
      logger.error(e, "Failed to invoke mksdcard at $toolLocation")
      return false
    }

  val errorOutput = ArrayList<String>()
  val stdOutput = ArrayList<String>()
  try {
    val status =
      GrabProcessOutput.grabProcessOutput(
        process,
        GrabProcessOutput.Wait.WAIT_FOR_READERS,
        object : IProcessOutput {
          override fun out(line: String?) {
            if (line != null) {
              stdOutput.add(line)
            }
          }

          override fun err(line: String?) {
            if (line != null) {
              errorOutput.add(line)
            }
          }
        },
      )

    if (status == 0) {
      return true
    }
  } catch (e: InterruptedException) {
    logger.warning("Interrupted")
  }
  for (error in errorOutput) {
    logger.warning("%1\$s", error)
  }
  logger.warning("Failed to create the SD card.")
  return false
}
