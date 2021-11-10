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

import java.lang.Math.abs

private const val SYSTEM_IMAGE_PREFIX = "system-images;"
private const val NUM_SYSTEM_IMAGE_HASH_DIVIDERS = 3
private const val API_PREFIX = "android-"
private const val API_OFFSET = 1
private const val VENDOR_OFFSET = 2
private const val ABI_OFFSET = 3

/**
 * Computes the system image repository hash from the information supplied on the managed
 * managed device dsl.
 *
 * @param version: the API version level from the dsl
 * @param imageSource: the system image source. Either "google" or "aosp"
 * @param abi: the abi for the system image.
 *
 * @return the hash for the system image repository with the given parameters.
 * A system image is not guaranteed to exist with the given values, but this gives the hash that the
 * sdkHandler can check.
 */
fun computeSystemImageHashFromDsl(version: Int, imageSource: String, abi: String) =
    "$SYSTEM_IMAGE_PREFIX${computeVersionString(version)};${computeVendorString(imageSource)};$abi"

private fun computeVersionString(version: Int) = "android-${version}"

private fun computeVendorString(imageSource: String) =
    when (imageSource) {
        "google" -> "google_apis_playstore"
        "aosp" -> "default"
        else -> error("""
            Unrecognized systemImagevendor: $imageSource. "google" or "aosp" expected.
        """.trimIndent())
    }

/**
 * Finds the list of system image hashes that are closest to the given target image.
 *
 * This will return all the system images with the smallest distance, given by [computeOffset].
 *
 * @param targetHash the hash to find suggestions for.
 * @param allHashes a list of all valid system image hashes.
 *
 * @return a list of hashes that are closest to [targetHash]. All hashes in the list will be
 * equidistant from the targetHash as computed by [computeOffset].
 */
fun findClosestHashes(targetHash: String, allHashes: List<String>): List<String> {
    val hashesByOffset = allHashes.filter { isValidSystemImageHash(it) }
        .map{ computeOffset(targetHash, it) to it }
        .sortedBy { it.first }
    if (hashesByOffset.isEmpty()) {
        return listOf()
    }
    val smallestOffset = hashesByOffset.first().first
    return hashesByOffset.filter {it.first == smallestOffset}.map {it.second}
}

/**
 * Computes the "distance" between two system image hashes.
 *
 * The distance is computed as follows:
 * 1. If api level is differnent, the difference is contributed to the offset.
 * 2. If the source is different, it contributes one to the offset.
 * 3. If the abi is different, it contributes one to the offset.
 *
 * @return the "distance" between the two system images.
 *
 * If either of the hashes do not represent a system image hash, [Int.MAX_VALUE] is returned.
 */
fun computeOffset(systemImageHash1: String, systemImageHash2: String): Int {
    if (!isValidSystemImageHash(systemImageHash1) || !isValidSystemImageHash(systemImageHash2)) {
        return Int.MAX_VALUE
    }
    val api1 = parseApiFromHash(systemImageHash1)
    val api2 = parseApiFromHash(systemImageHash2)
    if (api1 == null || api2 == null) {
        return Int.MAX_VALUE
    }
    var offset = abs(api1 - api2)
    if (getVendorFromHash(systemImageHash1) != getVendorFromHash(systemImageHash2)) {
        ++offset
    }
    if (getAbiFromHash(systemImageHash1) != getAbiFromHash(systemImageHash2)) {
        ++offset
    }
    return offset
}

/**
 * Does a check to see if the hash could represent a valid system image.
 */
fun isValidSystemImageHash(hash: String) =
    hash.startsWith(SYSTEM_IMAGE_PREFIX) &&
            hash.filter { it == ';' }.count() == NUM_SYSTEM_IMAGE_HASH_DIVIDERS

/**
 * Determine the api level of a system image hash.
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

/**
 * Find the vendor of a system image hash.
 */
fun getVendorFromHash(systemImageHash: String) =
    systemImageHash.split(";")[VENDOR_OFFSET]

/**
 * Find the abi of a system image hash.
 */
fun getAbiFromHash(systemImageHash: String) =
    systemImageHash.split(";")[ABI_OFFSET]
