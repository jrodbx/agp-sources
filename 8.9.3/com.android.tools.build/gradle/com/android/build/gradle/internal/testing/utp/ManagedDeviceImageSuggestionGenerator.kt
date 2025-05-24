/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment
import com.android.build.gradle.internal.computeAbiFromArchitecture
import com.android.build.gradle.internal.dsl.PAGE_16KB_SUFFIX
import com.android.testing.utils.computeSystemImageHashFromDsl
import com.android.testing.utils.isTvOrAutoSource
import com.android.testing.utils.parseApiFromHash
import com.android.testing.utils.parseExtensionFromHash
import com.android.testing.utils.parseSystemImageSourceFromHash
import com.android.testing.utils.parseAbiFromHash
import com.android.utils.CpuArchitecture
import kotlin.math.min

private const val MAX_SUGGESTIONS = 2

private fun isArm(architecture: CpuArchitecture) = when (architecture) {
    CpuArchitecture.ARM, CpuArchitecture.X86_ON_ARM -> true
    else -> false
}

/**
 * Suggests DSL changes to a [ManagedVirtualDevice] in order for it to
 * refer to a valid device. This is broken into different steps, and the
 * number of suggestions is capped by [MAX_SUGGESTIONS]. (See [message])
 *
 * @property architecture The architecture of the device that the
 * [ManagedVirtualDevice] will be run on. Affects which suggestions are made.
 * @property deviceName The name of the device from the DSL
 * @property sdkVersion The api level specified in the [ManagedVirtualDevice]
 * @property systemImageSource The source specified in the [ManagedVirtualDevice]
 * @property pageAlignmentSuffix the vendor page size suffix specified by the [ManagedVirtualDevice]
 * @property require64Bit Whether the [ManagedVirtualDevice] requires a 64 bit image.
 * @property allImages The list of all valid images that are available for download/use.
 */
class ManagedDeviceImageSuggestionGenerator (
    private val architecture: CpuArchitecture,
    private val deviceName: String,
    private val sdkVersion: Int,
    private val sdkExtensionVersion: Int?,
    private val systemImageSource: String,
    private val pageAlignmentSuffix: String,
    private val require64Bit: Boolean,
    private val allImages: List<String>
) {

    /**
     * The suggestion message generated from the given device information. This is broken
     * into different steps.
     *
     * First, we need to determine whether the device is invalid only for this architecture or _all_
     * architectures. This is important as the [ManagedVirtualDevice] may be perfectly valid, but
     * not intended to be run on the given device architecture. We need the ability to clarify
     * between this case, and the case where the DSL cannot refer to a valid image in it's present
     * state.
     *
     * Once we have framed the suggestion based on the scenario, we then take the first few
     * suggestions that can specify a valid image. The suggestions are ordered roughly by how much
     * the images are different from the desired image specified by the DSL.
     *
     * 1. Recommend a compatible non-ATD (Automated Test Device) image. (Only for aosp-atd and
     *     google-atd images).
     * 2. Recommend a compatible image from a different image source. (google-apis -> aosp, etc.)
     * 3a. Recommend the next available API level for the device.
     * 3b. If there is not available higher API level, recommend the highest available api level
     * 4. Recommend not requiring a 64 bit image, as this may change what tests are run in native
     *     code this is the last recommendation given.
     *
     * If no suggestion could be made, this likely means the system image source is invalid, and
     * another value in the dsl needs to be changed. In this case, we recommend the image source be
     * changed to one of the other available sources, in order to make suggestions on the next run.
     */
    val message: String by lazy {
        val suggestionMessage = StringBuilder()

        // Step 1: Determine if the system image is valid for the other architectures, or just
        // invalid overall.
        // This changes the way we frame the following suggestions
        suggestionMessage.append(checkForOtherArchitectureMessage())

        val suggestions = sequence {
            // Step 2:
            // If we are using an atd image, check to see if a non-ATD image exists.
            yield(checkAtdSuggestion())
            // Step 3:
            // Check to see if there are any alternative image sources.
            yield(checkAllSourcesSuggestion())
            // Step 4:
            // Check for an extension level recommendation
            yield(checkForExtensionSuggestion())
            // Step 5:
            // Check for an api recommendation
            yield(checkForOtherSdkVersionSuggestion())
            // Step 6:
            // See if unsetting require64Bit makes a difference.
            yield(checkFor32BitSuggestion())
            // Step 7:
            // See if a different page size is available
            // This is last as changing the page size will change how native code is run.
            yield(checkForOtherPageAlignment())
        }

        val recommendations = suggestions.filterNotNull().take(MAX_SUGGESTIONS).toList()

        if (recommendations.isNotEmpty()) {
            recommendations.forEachIndexed { index, suggestion ->
                suggestionMessage.appendLine().append("${index + 1}. $suggestion")
            }
        } else {
            // If we couldn't find a suggestion, and the image definition is invalid. We should let
            // them select a valid source. This will at least help us make suggestions in the
            // future.
            suggestValidSource()?.also {
                suggestionMessage.appendLine().append(it)
            }
        }

        suggestionMessage.toString()
    }

    private fun computeHash(
        otherArch: CpuArchitecture = architecture,
        otherRequire64Bit: Boolean = require64Bit,
        otherSdkVersion: Int = sdkVersion,
        otherSdkExtensionVersion: Int? = sdkExtensionVersion,
        otherImageSource: String = systemImageSource,
        otherPageAlignment: String = pageAlignmentSuffix,
    ): String {
        val abi = computeAbiFromArchitecture(
            otherRequire64Bit, otherSdkVersion, otherImageSource, otherArch)
        return computeSystemImageHashFromDsl(
            otherSdkVersion, otherSdkExtensionVersion, otherImageSource, otherPageAlignment, abi)
    }

    private fun checkForOtherArchitectureMessage(): String {
        val otherArch = if (isArm(architecture)) CpuArchitecture.X86_64 else CpuArchitecture.ARM

        val newHash = computeHash(otherArch = otherArch)
        return if (allImages.contains(newHash)) {
            "System Image for $deviceName does not exist for this architecture. However it is " +
                    "valid for $otherArch. This may be intended, but $deviceName cannot be used " +
                    "on this device.\n\n" +
                    "If this is not intended, try one of the following fixes:"
        } else {
            "System Image specified by $deviceName does not exist.\n\n" +
                    "Try one of the following fixes:"
        }
    }

    private fun checkAtdSuggestion(): String? {
        val newImageSource = when (systemImageSource) {
            "aosp-atd", "aosp_atd" -> "aosp"
            "google-atd", "google_atd" -> "google"
            // If we are not using an Automated Test Device image, we can skip this suggestion.
            else -> return null
        }
        val newHash = computeHash(otherImageSource = newImageSource)

        return if (allImages.contains(newHash)) {
            "Automated Test Device image does not exist for this architecture on the given " +
                    "sdkVersion. However, a normal emulator image does exist from a comparable " +
                    "source. Set systemImageSource = \"$newImageSource\" to use."
        } else {
            null
        }
    }

    private fun checkForExtensionSuggestion(): String? {
        // If not using an extension level, nothing to suggest.
        sdkExtensionVersion ?: return null

        val highestExtension = allImages.filter {
            parseApiFromHash(it) == sdkVersion
        }.maxOfOrNull {
            parseExtensionFromHash(it) ?: 0
        } ?: 0

        // First look for the next highest extension available.
        var nextAvailableExtension: Int? = null
        for (extension in sdkExtensionVersion..highestExtension) {
            val newHash = computeHash(otherSdkExtensionVersion = extension)
            if (allImages.contains(newHash)) {
                nextAvailableExtension = extension
                break
            }
        }

        if (nextAvailableExtension != null) {
            return "The system image does not exist with extension version $sdkExtensionVersion. " +
                    "However an image exists for extension version $nextAvailableExtension. Set " +
                    "sdkExtensionVersion = $nextAvailableExtension to use."
        }

        // The extension specified may be too high for the sdk version,
        // try to suggest the highest available.
        var latestAvailableExtension: Int? = null

        for (extension in min(sdkExtensionVersion, highestExtension) downTo 1) {
            val newHash = computeHash(otherSdkExtensionVersion = extension)
            if (allImages.contains(newHash)) {
                latestAvailableExtension = extension
                break
            }
        }

        return if (latestAvailableExtension != null) {
            "The system image does not presently exist for extension version " +
                    "$sdkExtensionVersion. The latest available extension version for SDK " +
                    "version $sdkVersion is $latestAvailableExtension. Set sdkExtensionVersion = " +
                    "$latestAvailableExtension to use. Be aware this may not have all extension " +
                    "apis needed for your application."
        } else {
            "No explicit extension levels exist for SDK version $sdkVersion. Either unset " +
                    "sdkExtensionVersion or try a different sdkVersion."
        }
    }

    private fun checkAllSourcesSuggestion(): String? {
        // For now we don't want to recommend playstore images because of the overhead to tests.
        val allRelevantSources = when (systemImageSource) {
            "aosp-atd", "aosp_atd" -> listOf("google-atd", "google")
            "google-atd", "google_atd" -> listOf("aosp-atd", "aosp")
            "aosp", "default" -> listOf("aosp-atd", "google", "google-atd")
            "google", "google_apis" -> listOf("google-atd", "aosp", "aosp-atd")
            else -> listOf("aosp-atd", "aosp", "google-atd", "google")
        }
        val validSources = allRelevantSources.filter {
            val newHash = computeHash(otherImageSource = it)

            allImages.contains(newHash)
        }

        return if (validSources.isNotEmpty()) {
            "The image does not exist from $systemImageSource for this architecture on the given " +
                    "sdkVersion. However, other sources exist. Set systemImageSource to any of " +
                    "$validSources to use."
        } else {
            null
        }
    }

    private fun checkForOtherSdkVersionSuggestion(): String? {
        // Figure out how far we should search.
        val highestApi = allImages.maxOfOrNull {
            parseApiFromHash(it) ?: 0
        } ?: 0

        var nextAvailableLevel: Int? = null
        for (level in sdkVersion..highestApi) {
            val newHash = computeHash(otherSdkVersion = level)
            if (allImages.contains(newHash)) {
                nextAvailableLevel = level
                break
            }
        }

        if (nextAvailableLevel != null) {
            return "The system image does not exist for sdkVersion $sdkVersion. However an image " +
                    "exists for sdkVersion $nextAvailableLevel. Set sdkVersion = " +
                    "$nextAvailableLevel to use."
        }

        var latestAvailableLevel: Int? = null

        // If an upgraded sdkVersion does not exist. Then we should find the latest valid version.
        for (level in min(sdkVersion, highestApi) downTo 1) {
            val newHash = computeHash(otherSdkVersion = level)
            if (allImages.contains(newHash)) {
                latestAvailableLevel = level
                break
            }
        }

        if (latestAvailableLevel != null) {
            return "The system image does not presently exist for sdkVersion $sdkVersion. The " +
                    "latest available sdkVersion is $latestAvailableLevel. Set sdkVersion = " +
                    "$latestAvailableLevel to use."
        }

        return null
    }

    private fun checkFor32BitSuggestion(): String? {
        // On ARM require64 does nothing. And if we are not require64Bit, nothing to suggest.
        if (isArm(architecture) || !require64Bit) {
            return null
        }

        val newHash = computeHash(otherRequire64Bit = false)
        if (!allImages.contains(newHash)) {
            // 32 bit image does not exist.
            return null
        }
        return "There is an available X86 image for sdkVersion $sdkVersion. Set require64Bit = " +
                "false to use. Be aware tests involving native X86_64 code will not be run with " +
                "this change."
    }

    private fun suggestValidSource(): String? {
        val sources = allImages.filter {
                if (isArm(architecture)) {
                    parseAbiFromHash(it) == "arm64-v8a"
                } else {
                    parseAbiFromHash(it)?.startsWith("x86") == true
                }
            }.mapNotNull {
                parseSystemImageSourceFromHash(it)
            }.filterNot {
                isTvOrAutoSource(it)
            }.toMutableSet()
        if (sources.isEmpty() ) {
            return null
        }
        sources.addAll(
            listOf("aosp", "aosp-atd", "google", "google-atd")
        )
        // Do not recommend what has already been tried.
        sources.remove(systemImageSource)

        return "Could not form a valid suggestion for the device $deviceName.\n" +
                "This is likely due to an invalid image source. The source specified by " +
                "$deviceName is \"$systemImageSource\".\n" +
                "Set systemImageSource to any of $sources to get more suggestions."
    }

    private fun checkForOtherPageAlignment(): String? {
        val otherAlignment = when(pageAlignmentSuffix) {
            PAGE_16KB_SUFFIX -> ""
            else -> PAGE_16KB_SUFFIX
        }

        if (allImages.contains(computeHash(otherPageAlignment = otherAlignment))) {
            val alignment = when (otherAlignment) {
                PAGE_16KB_SUFFIX -> PageAlignment.FORCE_16KB_PAGES
                else -> PageAlignment.FORCE_4KB_PAGES
            }
            return "There is a valid system image for a different page alignment. Set " +
                    "pageAlignment = PageAlignment.$alignment to use. Be aware using a different " +
                    "page alignment will affect how native code is run for testing purposes."
        }
        return null
    }
}
