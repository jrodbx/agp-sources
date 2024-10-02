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

package com.android.testing.utils

private const val SYSTEM_IMAGE_PREFIX = "system-images;"
private const val API_PREFIX = "android-"
private const val API_OFFSET = 1
private const val VENDOR_OFFSET = 2
private const val ABI_OFFSET = 3

/**
 * Computes the system image repository hash from the information supplied on the managed
 * managed device dsl.
 *
 * @param version: the API version level from the dsl
 * @param imageSource: the system image source.
 * @param abi: the abi for the system image.
 *
 * @return the hash for the system image repository with the given parameters.
 * A system image is not guaranteed to exist with the given values, but this gives the hash that the
 * sdkHandler can check.
 */
fun computeSystemImageHashFromDsl(version: Int, imageSource: String, abi: String) =
    "$SYSTEM_IMAGE_PREFIX${computeVersionString(version)};${computeVendorString(imageSource)};$abi"

private fun computeVersionString(version: Int) = "android-${version}"

fun computeVendorString(imageSource: String) =
    when (imageSource) {
        "google" -> "google_apis"
        "google-atd" -> "google_atd"
        "aosp" -> "default"
        "aosp-atd" -> "aosp_atd"
        else -> imageSource
    }

fun isTvOrAutoSource(imageSource: String) =
    imageSource.contains("-tv") || imageSource.contains("-auto")

fun isTvOrAutoDevice(deviceName: String) =
    deviceName.contains("TV") || deviceName.contains("Auto")

/**
 * Determine the api level of a system image hash
 */
fun parseApiFromHash(systemImageHash: String): Int? {
    val apiComponent = systemImageHash.split(";")[API_OFFSET]
    if (!apiComponent.startsWith(API_PREFIX)) {
        return null
    }
    return try {
        apiComponent.substringAfter(API_PREFIX).toInt()
    } catch (e: NumberFormatException) {
        null
    }
}

fun parseVendorFromHash(systemImageHash: String): String? =
    systemImageHash.split(";").getOrNull(VENDOR_OFFSET)

fun parseAbiFromHash(systemImageHash: String): String? =
    systemImageHash.split(";").getOrNull(ABI_OFFSET)
