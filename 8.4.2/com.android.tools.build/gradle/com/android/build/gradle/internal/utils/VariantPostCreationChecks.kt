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

package com.android.build.gradle.internal.utils

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.cxx.configure.AbiConfigurationKey
import com.android.build.gradle.internal.cxx.configure.AbiConfigurator
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.android.ide.common.build.GenericBuiltArtifact
import com.android.ide.common.build.GenericBuiltArtifactsSplitOutputMatcher
import com.android.ide.common.build.GenericFilterConfiguration
import com.google.common.base.Joiner
import com.google.common.base.Strings
import java.util.Arrays
import java.util.stream.Collectors

/**
 * This function checks if the build is targeting Riscv platform and fails the build if the project
 * includes use of RenderScript. This is to prevent runtime failure as RenderScript is not supported on Riscv.
 */
fun restrictRenderScriptOnRiscv(
    dslServices: DslServices,
    creationConfig: ConsumableCreationConfig,
    buildFeatures: BuildFeatureValues,
    globalConfig: GlobalTaskCreationConfig
) {
    if (!buildFeatures.renderScript) {
        return
    }
    val nativeBuildCreationConfig = creationConfig.nativeBuildCreationConfig ?: return
    val projectOptions = dslServices.projectOptions
    if (!globalConfig.versionedNdkHandler.ndkPlatform.isConfigured) {
        return
    }
    val ndk = globalConfig.versionedNdkHandler.ndkPlatform.getOrThrow()
    val ndkMetaAbiList = NdkAbiFile(ndkMetaAbisFile(ndk.ndkDirectory)).abiInfoList
    val validAbiList =
        AbiConfigurator(
            AbiConfigurationKey(
                ndkMetaAbiList = ndkMetaAbiList,
                ndkHandlerSupportedAbis = ndk.supportedAbis.toSet(),
                ndkHandlerDefaultAbis = ndk.defaultAbis.toSet(),
                externalNativeBuildAbiFilters = nativeBuildCreationConfig.externalNativeBuild?.abiFilters?.get() ?: setOf(),
                nativeBuildCreationConfig.ndkConfig.abiFilters,
                globalConfig.splits.abiFilters.toSet(),
                projectOptions[BooleanOption.BUILD_ONLY_TARGET_ABI],
                projectOptions[StringOption.IDE_BUILD_TARGET_ABI]
            )
        ).validAbis.toList()
    val riscvAbis = validAbiList.filter{ it.contains(SdkConstants.ABI_RISCV64, true) }
    if (riscvAbis.isNotEmpty()) {
        dslServices.issueReporter.reportError(
            IssueReporter.Type.GENERIC,
            "Project ${dslServices.projectInfo.name} uses RenderScript. Cannot build for ABIs: $riscvAbis because RenderScript is not supported on Riscv."
        )
    }
}
