/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.options.StringOption
import com.android.utils.cxx.CxxDiagnosticCode.ABI_HAS_ONLY_32_BIT_SUPPORT
import com.android.utils.cxx.CxxDiagnosticCode.ABI_IS_INVALID
import com.android.utils.cxx.CxxDiagnosticCode.ABI_IS_UNSUPPORTED

data class AbiConfigurationKey(
    val ndkMetaAbiList: List<AbiInfo>,
    val ndkHandlerSupportedAbis: Set<String>,
    val ndkHandlerDefaultAbis: Set<String>,
    val externalNativeBuildAbiFilters: Set<String>,
    val ndkConfigAbiFilters: Set<String>,
    val splitsFilterAbis: Set<String>,
    val ideBuildOnlyTargetAbi: Boolean,
    val ideBuildTargetAbi: String?
)

data class AbiConfiguration(
    val allAbis: Set<String>,
    val validAbis: Set<String>
)

/**
 * This class is responsible for determining which ABIs are needed for the build based on the
 * relevant contents of build.gradle DSL.
 */
class AbiConfigurator(
    private val key: AbiConfigurationKey
) {
    private val configuration: AbiConfiguration
    val allAbis: Set<String> get() = configuration.allAbis
    val validAbis: Set<String> get() = configuration.validAbis

    /** Sort and join a list of strings for an error message */
    private fun sortAndJoinAbiStrings(elements: Collection<String>): String {
        return elements.sorted().joinToString(", ")
    }

    private fun sortAndJoinAbi(elements: Set<String>): String {
        return elements.sorted().joinToString(", ")
    }

    init {
        with(key) {
            val supports64Bits = ndkMetaAbiList.filter { it.bitness == 64 }.map { it.name }.toSet()
            val legalAbiNames = ndkMetaAbiList.map { it.name }.toSet()
            val toleratedAbiNames = Abi.values().map { it.tag }
            val userChosenAbis =
                externalNativeBuildAbiFilters union splitsFilterAbis union ndkConfigAbiFilters
            val userMistakes =
                userChosenAbis subtract ndkHandlerSupportedAbis
            if (userMistakes.isNotEmpty()) {
                errorln(
                    ABI_IS_UNSUPPORTED,
                    "ABIs [${sortAndJoinAbiStrings(userMistakes)}] are not supported for platform. " +
                            "Supported ABIs are [${
                                sortAndJoinAbiStrings(
                                    ndkHandlerSupportedAbis
                                )
                            }]."
                )
            }

            val allAbis: Set<String>
            val validAbis: Set<String>
            val configurationAbis: Set<String>
            if (userChosenAbis.isEmpty()) {
                // The user didn't explicitly name any ABIs so return the default set
                allAbis = ndkHandlerDefaultAbis
                configurationAbis = ndkHandlerDefaultAbis
            } else {
                // The user explicitly named some ABIs
                val recognizeAbleAbiStrings = ndkMetaAbiList.map { it.name }.toSet()
                val selectedAbis : Set<String> =
                    sequenceOf(
                        externalNativeBuildAbiFilters,
                        ndkConfigAbiFilters,
                        splitsFilterAbis
                    )
                        .filter { it.isNotEmpty() }
                        .fold(recognizeAbleAbiStrings) { total, next -> total intersect next }

                // Produce the list of expected JSON files. This list includes possibly invalid ABIs
                // so that generator can create fallback JSON for them.
                allAbis = selectedAbis union userMistakes
                // These are ABIs that are available on the current platform
                configurationAbis = selectedAbis
            }

            // Lastly, if there is an injected ABI set and none of the ABIs is actually buildable by
            // this project then issue an error.
            if (ideBuildOnlyTargetAbi && !ideBuildTargetAbi.isNullOrEmpty()) {
                val injectedAbis = ideBuildTargetAbi.split(",").map { it.trim() }
                val injectedLegalAbis = injectedAbis.filter { legalAbiNames.contains(it) }
                validAbis = if (injectedLegalAbis.isEmpty()) {
                    // The user (or android studio) didn't select any legal ABIs, that's an error
                    // since there's nothing to build. Fall back to the ABIs from build.gradle so
                    // that there's something to show the user.
                    errorln(
                        ABI_IS_UNSUPPORTED,
                        "ABIs [$ideBuildTargetAbi] set by " +
                                "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                                "flag is not supported. Supported ABIs " +
                                "are [${sortAndJoinAbiStrings(allAbis)}]."
                    )
                    configurationAbis
                } else {
                    val invalidAbis = injectedAbis
                        .filter { !legalAbiNames.contains(it) }
                    if (invalidAbis.isNotEmpty()) {
                        val (toleratedInvalid, nontoleratedInvalid) = invalidAbis
                            .partition { toleratedAbiNames.contains(it) }
                        if (nontoleratedInvalid.isNotEmpty()) {
                            // The user (or android studio) selected some illegal ABIs. Give a warning and
                            // continue on.
                            warnln(
                                ABI_IS_INVALID,
                                "ABIs [$ideBuildTargetAbi] set by " +
                                        "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                                        "flag contained '${sortAndJoinAbiStrings(nontoleratedInvalid)}' which is invalid."
                            )
                        }
                        if (toleratedInvalid.isNotEmpty()) {
                            // The user (or android studio) selected some illegal ABIs but they are known (probably old
                            // and deprecated) so we just issue an info message
                            infoln(
                                "ABIs [$ideBuildTargetAbi] set by " +
                                        "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                                        "flag contained '${sortAndJoinAbiStrings(toleratedInvalid)}' which is known but invalid for this NDK."
                            )
                        }
                    }

                    val legalButNotTargetedByConfiguration =
                        injectedLegalAbis subtract configurationAbis
                    val validAbisInPreferenceOrder =
                        if (legalButNotTargetedByConfiguration.isNotEmpty()) {
                            // The user (or android studio) selected some ABIs that are valid but that
                            // aren't targeted by this build configuration.
                            infoln(
                                "ABIs [$ideBuildTargetAbi] set by " +
                                        "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                                        "flag contained '${sortAndJoinAbi(
                                            legalButNotTargetedByConfiguration
                                        )}' " +
                                        "not targeted by this project."
                            )
                            // Keep ABIs actually targeted
                            (injectedLegalAbis intersect configurationAbis).toList()
                        } else {
                            injectedLegalAbis
                        }

                    // When Android Studio sends multiple ABIs it is meant as a precedence list
                    // we build the first one that is available in this project.
                    validAbisInPreferenceOrder.take(1).toSet()
                }
            } else {
                validAbis = configurationAbis

                // Warn if validAbis does not include at least one 64-bit ABI.
                // See: https://android-developers.googleblog.com/2019/01/get-your-apps-ready-for-64-bit.html
                if (validAbis.isNotEmpty() && !validAbis.any { supports64Bits.contains(it) }) {
                    warnln(
                        ABI_HAS_ONLY_32_BIT_SUPPORT,
                        "This app only has 32-bit [${validAbis.joinToString(",") { it }}] " +
                                "native libraries. Beginning August 1, 2019 Google Play store requires " +
                                "that all apps that include native libraries must provide 64-bit versions. " +
                                "For more information, visit https://g.co/64-bit-requirement"
                    )
                }
            }
            configuration = AbiConfiguration(allAbis, validAbis)
        }
    }
}
