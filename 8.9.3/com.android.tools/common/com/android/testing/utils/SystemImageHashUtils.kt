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
private const val API_EXTENSION_PREFIX = "-ext"
private const val PAGE_16K_SOURCE_SUFFIX = "_ps16k"
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
fun computeSystemImageHashFromDsl(
    version: Int,
    extensionVersion: Int?,
    imageSource: String,
    pageAlignmentSuffix: String,
    abi: String) =
    "$SYSTEM_IMAGE_PREFIX${computeVersionString(version, extensionVersion)};" +
            "${computeVendorString(imageSource, pageAlignmentSuffix)};$abi"

fun computeVersionString(version: Int, extensionVersion: Int?): String {
    val extensionSuffix = if (extensionVersion != null) {
        "-ext$extensionVersion"
    } else {
        ""
    }
    return "android-${version}$extensionSuffix"
}

fun computeVendorString(imageSource: String, pageAlignmentSuffix: String) =
    computeImageSource(imageSource) + pageAlignmentSuffix

private fun computeImageSource(imageSource: String) =
    when (imageSource) {
        "google" -> "google_apis"
        "google-atd" -> "google_atd"
        "aosp" -> "default"
        "aosp-atd" -> "aosp_atd"
        else -> imageSource
    }

fun isTvOrAutoSource(imageSource: String) =
    imageSource.contains("-tv") || imageSource.contains("-auto")

fun is16kPageSource(vendorString: String) =
    vendorString.contains(PAGE_16K_SOURCE_SUFFIX)

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
        apiComponent.substringAfter(API_PREFIX).substringBefore(API_EXTENSION_PREFIX).toInt()
    } catch (e: NumberFormatException) {
        null
    }
}

fun parseExtensionFromHash(systemImageHash: String): Int? {
    val apiComponent = systemImageHash.split(";")[API_OFFSET]
    if (!apiComponent.startsWith(API_PREFIX)) {
        return null
    }
    return try {
        apiComponent.substringAfter(API_EXTENSION_PREFIX)?.toInt()
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Returns the vendor string for the given system image
 *
 * The vendor string is different from the system image source as it may contain
 * additional information such as page size.
 */
fun parseVendorFromHash(systemImageHash: String): String? =
    systemImageHash.split(";").getOrNull(VENDOR_OFFSET)

/**
 * Returns the system image source for the given system image.
 *
 * The source will be one of "google_apis", "google_apis_playstore", "default", "google_atd",
 * "aosp_atd". For the actual vendor string associated with the hash see [parseVendorFromHash]
 */
fun parseSystemImageSourceFromHash(systemImageHash: String): String? =
    parseVendorFromHash(systemImageHash)?.removeSuffix(PAGE_16K_SOURCE_SUFFIX)

fun parseAbiFromHash(systemImageHash: String): String? =
    systemImageHash.split(";").getOrNull(ABI_OFFSET)
